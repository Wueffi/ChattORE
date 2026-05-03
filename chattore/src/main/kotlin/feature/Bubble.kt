package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Flags
import co.aikar.commands.annotation.Single
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.velocity.contexts.OnlinePlayer
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.openredstone.chattore.*
import java.util.UUID

val seeGlobalChatEnabled: Setting<Boolean> = Setting("seeGlobalChat")
const val BUBBLE_OWNED: String = "bubble-owned"

fun PluginScope.createBubbleFeature(
    messenger: Messenger,
    database: Storage,
): BubbleManager {
    val bubbleManager = BubbleManager()
    commandManager.apply {
        commandContexts.registerIssuerOnlyContext(Bubble::class.java) { ctx ->
            val sender = ctx.sender as? Player
                ?: throw InvalidCommandArgument("This command can only be used by players!")
            val bubble = bubbleManager.getBubbleByPlayer(sender)
                ?: throw InvalidCommandArgument("You are not in a Chat-Bubble!")
            if (ctx.hasFlag(BUBBLE_OWNED) && !bubble.isOwner(sender.uniqueId)) {
                val ownerName = proxy.getPlayer(bubble.owner)
                    .map { it.username }
                    .orElse(bubble.owner.toString())
                throw InvalidCommandArgument("You are not the owner of this Chat-Bubble! ($ownerName is.)")
            }
            bubble
        }
    }
    registerCommands(BubbleCommand(messenger, proxy, database, bubbleManager))
    return bubbleManager
}

@CommandAlias("bubble|bb")
@CommandPermission("chattore.bubble")
private class BubbleCommand(
    private val messenger: Messenger,
    private val proxy: ProxyServer,
    private var database: Storage,
    private var bubbleManager: BubbleManager
) : BaseCommand() {

    @Subcommand("create")
    @CommandAlias("blow")
    fun create(sender: Player) {
        if (bubbleManager.getBubbleByPlayer(sender) != null)
            throw ChattoreException("You are already in a Chat-Bubble!")
        bubbleManager.createBubble(sender.uniqueId)
        messenger.setCacheDirty()
        sender.sendInfo("Successfully created Chat-Bubble!")
    }

    @Subcommand("invite")
    fun invite(sender: Player, bubble: Bubble, @Single target: OnlinePlayer) {
        val player: Player = target.player
        when {
            bubble.isInvited(player.uniqueId)
                -> sender.sendInfo("${player.username} is already invited!")

            bubble.containsPlayer(player.uniqueId)
                -> sender.sendInfo("${player.username} is already in the Chat-Bubble!")

            else -> {
                bubble.invitePlayer(player.uniqueId)
                sender.sendInfo("Successfully invited ${player.username}!")
                player.sendInfo("${sender.username} invited you to their Chat-Bubble!")
            }
        }
    }

    @Subcommand("join")
    fun join(sender: Player, @Single target: OnlinePlayer) {
        if (bubbleManager.getBubbleByPlayer(sender) != null)
            throw ChattoreException("You are already in a Chat-Bubble!")

        val player: Player = target.player
        val bubble = bubbleManager.getBubbleByPlayer(player)
            ?: throw ChattoreException("Target is not in a Chat-Bubble!")

        if (!bubble.isInvited(sender.uniqueId) && bubble.isPrivate)
            throw ChattoreException("You were not invited to the Chat-Bubble!")

        bubble.addPlayer(sender.uniqueId)
        messenger.setCacheDirty()
        bubble.sendInfos(
            sender,
            "You successfully joined ${player.username}'s Chat-Bubble!",
            "${sender.username} joined your Chat-Bubble!",
        )
    }

    @Subcommand("leave")
    fun leave(sender: Player, bubble: Bubble) {
        bubble.removePlayer(sender.uniqueId)
        messenger.setCacheDirty()
        sender.sendInfo("You successfully left the Chat-Bubble!")
        if (bubble.players.isEmpty()) {
            bubbleManager.removeBubble(bubble)
            return
        }
        if (bubble.owner == sender.uniqueId) {
            val newOwner = bubble.players.first()
            bubble.owner = newOwner
            proxy.playerOrNull(newOwner)?.sendInfo("You are now the owner of the Chat-Bubble!")
        }
        bubble.broadcastInfo("${sender.username} left your Chat-Bubble!")
    }

    @Subcommand("delete")
    @CommandAlias("pop")
    fun delete(sender: Player, @Flags(BUBBLE_OWNED) bubble: Bubble) {
        bubble.sendInfos(
            sender,
            "You popped the the Chat-Bubble!",
            "${sender.username} popped your Chat-Bubble!",
        )
        bubbleManager.removeBubble(bubble)
        messenger.setCacheDirty()
    }

    @Subcommand("kick")
    fun kick(sender: Player, @Flags(BUBBLE_OWNED) bubble: Bubble, @Single target: OnlinePlayer) {
        val player: Player = target.player
        if (player.uniqueId == sender.uniqueId)
            throw ChattoreException("You cannot kick yourself!")

        bubble.removePlayer(player.uniqueId)
        messenger.setCacheDirty()
        player.sendInfo("You were kicked out of the Chat-Bubble!")
        bubble.sendInfos(
            sender,
            "You kicked ${player.username} from the Chat-Bubble!",
            "${sender.username} kicked ${player.username} from your Chat-Bubble!",
        )
    }

    @Subcommand("setPrivate")
    @CommandCompletion("true|false")
    fun setPrivate(sender: Player, @Flags(BUBBLE_OWNED) bubble: Bubble, boolean: Boolean) {
        bubble.isPrivate = boolean
        bubble.sendInfos(
            sender,
            "The Chat-Bubble is now ${if (boolean) "private" else "public"}!",
            "${sender.username} set the Chat-Bubble to ${if (boolean) "private" else "public"}!",
        )
    }

    @Subcommand("list")
    fun list(sender: Player) {
        if (BubbleManager.getBubbles().isEmpty()) {
            sender.sendInfo("There are currently no Chat-Bubbles!")
            return
        }
        sender.sendRichMessage("<yellow>Bubbles:</yellow>")
        for (bubble in BubbleManager.getBubbles()) {
            val playersString = bubble.players
                .mapNotNull { uuid -> proxy.getPlayer(uuid).orElse(null)?.username }
                .joinToString(", ")
            val firstPlayer = bubble.players.firstOrNull()
                ?.let { proxy.getPlayer(it).orElse(null)?.username }
            sender.sendMessage(
                Component.text("$playersString <gray>|</gray> <gray>[</gray><green>Join</green><gray>]</gray>")
                    .clickEvent(ClickEvent.suggestCommand("/bubble join $firstPlayer"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to join bubble")))
            )
        }
    }

    @Subcommand("forcedelete")
    @CommandAlias("burst")
    @CommandPermission("chattore.bubble.manage")
    fun forcedelete(sender: Player, @Single target: OnlinePlayer) {
        val player: Player = target.player
        val bubble = bubbleManager.getBubbleByPlayer(player)
            ?: throw ChattoreException("Target is not in a Chat-Bubble!")
        bubble.broadcastInfo("The Chat-Bubble was burst by ${sender.username}!")
        bubbleManager.removeBubble(bubble)
        messenger.setCacheDirty()
        sender.sendInfo("You successfully burst ${player.username}'s Chat-Bubble!")
    }

    @CommandAlias("shout")
    fun shout(sender: Player, message: String) {
        proxy.eventManager.fireAndForget(PlayerChatEvent(sender, message))
    }

    @CommandAlias("seeglobalchat|gc")
    @CommandCompletion("true|false")
    fun seeGlobalChat(sender: Player, boolean: Boolean) {
        database.setSetting(seeGlobalChatEnabled, sender.uniqueId, boolean)
        messenger.setCacheDirty()
        sender.sendInfo(
            if (boolean) "You will now see global chat inside your Chat-Bubble!"
            else "You won't see global chat inside your Chat-Bubble anymore!"
        )
    }

    private fun Bubble.sendInfos(you: Player, yourMessage: String, theirMessage: String) {
        players.forEach { uuid ->
            val player = proxy.playerOrNull(uuid) ?: return@forEach
            player.sendInfo(if (player == you) yourMessage else theirMessage)
        }
    }

    private fun Bubble.broadcastInfo(message: String) {
        players.forEach { uuid ->
            proxy.playerOrNull(uuid)?.sendInfo(message)
        }
    }
}

data class Bubble(
    var owner: UUID,
    val players: MutableSet<UUID>,
    private val invitedPlayers: MutableSet<UUID>,
    var isPrivate: Boolean
) {
    fun addPlayer(uuid: UUID): Boolean {
        if (!isPrivate) return players.add(uuid)

        if (uuid !in invitedPlayers) return false

        invitedPlayers.remove(uuid)
        return players.add(uuid)
    }

    fun removePlayer(uuid: UUID): Boolean {
        return players.remove(uuid)
    }

    fun invitePlayer(uuid: UUID): Boolean {
        return invitedPlayers.add(uuid)
    }

    fun containsPlayer(uuid: UUID): Boolean {
        return players.contains(uuid)
    }

    fun isInvited(uuid: UUID): Boolean {
        return invitedPlayers.contains(uuid)
    }

    fun isOwner(uuid: UUID): Boolean {
        return owner == uuid
    }
}

class BubbleManager {
    companion object {
        private val bubbles: MutableList<Bubble> = mutableListOf()

        fun getBubbles(): List<Bubble> {
            return bubbles
        }
    }

    fun createBubble(player: UUID) {
        bubbles.add(Bubble(player, mutableSetOf(player), mutableSetOf(), false))
    }

    fun removeBubble(bubble: Bubble) {
        bubbles.remove(bubble)
    }

    fun getBubbleByPlayer(player: Player): Bubble? {
        return bubbles.firstOrNull { it.players.contains(player.uniqueId) }
    }
}
