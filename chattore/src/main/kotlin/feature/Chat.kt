package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import org.openredstone.chattore.ChattoreException
import org.openredstone.chattore.Messenger
import org.openredstone.chattore.PluginScope
import org.openredstone.chattore.sendSimpleMM
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ChatConfirmationConfig(
    val regexes: List<String> = listOf(),
)

fun PluginScope.createChatFeature(
    messenger: Messenger,
    config: ChatConfirmationConfig,
    bubbleManager: BubbleManager,
) {
    val confirmations = ChatConfirmations(config, logger)
    val chatListener = ChatListener(confirmations, logger, messenger, bubbleManager)
    registerListeners(chatListener)
    registerCommands(confirmations.ConfirmMessage(), Shout(chatListener, confirmations))
}

class ChatConfirmations(
    config: ChatConfirmationConfig,
    private val logger: Logger,
) {
    private val regexes = config.regexes.mapNotNull { pattern ->
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
            .onFailure { logger.error("Invalid regex $pattern: ${it.message}") }
            .getOrNull()
    }

    private val flags = ConcurrentHashMap<UUID, Pair<String, () -> Unit>>()
    fun submit(player: Player, message: String, proceed: () -> Unit) {
        val matches = regexes.filter { it.containsMatchIn(message) }
        if (matches.isEmpty()) {
            flags.remove(player.uniqueId)
            proceed()
            return
        }
        fun String.highlight(r: Regex) = r.replace(this) { match -> "<red>${match.value}</red>" }
        val highlighted = matches.fold(message, String::highlight)
        logger.info("${player.username} (${player.uniqueId}) Attempting to send flagged message: $message")
        player.sendSimpleMM(
            "<red><bold>The following message was not sent because it contained " +
                "potentially inappropriate language:<newline><reset><message><newline><red>To send this message anyway, run " +
                "<gray>/confirmmessage<red>.",
            highlighted,
        )
        flags[player.uniqueId] = message to proceed
        return
    }

    @CommandAlias("confirmmessage")
    @CommandPermission("chattore.confirmmessage")
    inner class ConfirmMessage : BaseCommand() {
        @Default
        fun default(player: Player) {
            val (message, proceed) = flags[player.uniqueId]
                ?: throw ChattoreException("You have no message to confirm!")
            player.sendRichMessage("<red>Override recognized")
            flags.remove(player.uniqueId)
            logger.info("${player.username} (${player.uniqueId}) FLAGGED MESSAGE OVERRIDE: $message")
            proceed()
        }
    }
}

private class ChatListener(
    private val confirmations: ChatConfirmations,
    private val logger: Logger,
    private val messenger: Messenger,
    private val bubbleManager: BubbleManager,
) {
    @Subscribe
    fun onChatEvent(event: PlayerChatEvent) {
        val player = event.player
        val message = event.message
        confirmations.submit(player, message) {
            handleChat(player, message, bubbleManager.getBubbleByPlayer(player))
        }
    }

    // NOTE: confirmations.submit is not done here in a single place so that we get
    // the bubble the player is in *at the time of confirmation*, not when the message was sent,
    // because otherwise you could leave (or be kicked) from your bubble and still send messages there
    // if you had something to confirm. Ideally, you wouldn't be able to confirm that anymore, but this'll do for now
    fun handleChat(player: Player, message: String, bubble: Bubble?) {
        player.currentServer.ifPresent { server ->
            if (bubble != null) {
                logger.info("[Bubble] ${player.username} (${player.uniqueId}): $message")
                messenger.broadcastBubbleMessage(player, message, bubble)
            } else {
                logger.info("${player.username} (${player.uniqueId}): $message")
                messenger.broadcastChatMessage(server.serverInfo.name, player, message)
            }
        }
    }
}

@CommandAlias("shout")
@CommandPermission("chattore.bubble")
private class Shout(
    private val chatListener: ChatListener,
    private val confirmations: ChatConfirmations,
) : BaseCommand() {
    @Default
    fun shout(sender: Player, message: String) {
        confirmations.submit(sender, message) {
            chatListener.handleChat(sender, message, bubble = null)
        }
    }
}
