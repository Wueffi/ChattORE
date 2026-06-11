package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import org.openredstone.chattore.*
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
    registerCommands(confirmations.ConfirmMessage(), Shout(chatListener))
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
        val bubble = bubbleManager.getBubbleByPlayer(player)
        if (bubble == null) {
            handleGlobalChat(player, message)
            return
        }
        confirmations.submit(player, message) {
            if (bubbleManager.getBubbleByPlayer(player) !== bubble) {
                player.sendError("You are no longer in the bubble you're trying to send a message to")
                return@submit
            }
            messenger.broadcastBubbleMessage(player, message, bubble)
        }
    }

    fun handleGlobalChat(player: Player, message: String) = confirmations.submit(player, message) {
        messenger.broadcastChatMessage(player, message)
    }
}

@CommandAlias("shout")
@CommandPermission("chattore.bubble")
private class Shout(
    private val chatListener: ChatListener,
) : BaseCommand() {
    @Default
    fun shout(sender: Player, message: String) {
        chatListener.handleGlobalChat(sender, message)
    }
}
