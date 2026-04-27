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
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.openredstone.chattore.*
import java.util.UUID

val seeGlobalChatEnabled: Setting<Boolean> = Setting("seeGlobalChat")

fun createBubbleManagerFeature(): BubbleManager {
    return BubbleManager()
}

fun PluginScope.createBubbleFeature(
    messenger: Messenger,
    userCache: UserCache,
    database: Storage,
    bubbleManager: BubbleManager
) {
    commandManager.apply {
        commandContexts.registerContext(Bubble::class.java) { ctx ->
            val sender = ctx.sender as? Player
                ?: throw InvalidCommandArgument("This command can only be used by players!")
            val bubble = bubbleManager.getBubbleByPlayer(sender)
                ?: throw InvalidCommandArgument("You are not in a Chat-Bubble!")
            if (ctx.hasFlag("bubble-owned") && !bubble.isOwner(sender.uniqueId)) {
                val ownerName = proxy.getPlayer(bubble.owner)
                    .map { it.username }
                    .orElse(bubble.owner.toString())
                throw InvalidCommandArgument("You are not the owner of this Chat-Bubble! ($ownerName is.)")
            }
            bubble
        }
    }

    registerCommands(BubbleCommand(messenger, proxy, userCache, database, bubbleManager))
}

@CommandAlias("bubble|bb")
@CommandPermission("chattore.bubble")
private class BubbleCommand(
    private val messenger: Messenger,
    private val proxy: ProxyServer,
    private var userCache: UserCache,
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
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
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
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
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
        bubble.players.forEach { uuid ->
            val member = proxy.getPlayer(uuid).orElse(null) ?: return@forEach
            if (member == sender) {
                member.sendInfo("You successfully joined ${player.username}'s Chat-Bubble!")
            } else {
                member.sendInfo("${sender.username} joined your Chat-Bubble!")
            }
        }
    }

    @Subcommand("leave")
    fun leave(sender: Player, bubble: Bubble) {
        val bubbleId = bubbleManager.getBubbleId(sender.uniqueId)!!
        bubble.removePlayer(sender.uniqueId)
        messenger.setCacheDirty()
        sender.sendInfo("You successfully left the Chat-Bubble!")
        if (bubble.players.isEmpty()) {
            bubbleManager.removeBubble(bubbleId)
            return
        }
        bubble.players.forEach { uuid ->
            proxy.getPlayer(uuid).orElse(null)?.sendInfo("${sender.username} left your Chat-Bubble!")
        }
    }

    @Subcommand("delete")
    @CommandAlias("pop")
    fun delete(sender: Player, @Flags("bubble-owned") bubble: Bubble) {
        val bubbleId = bubbleManager.getBubbleId(sender.uniqueId)!!
        bubble.players.forEach { uuid ->
            val member = proxy.getPlayer(uuid).orElse(null) ?: return@forEach
            if (member == sender) {
                member.sendInfo("You popped the Chat-Bubble!")
            } else {
                member.sendInfo("${sender.username} popped your Chat-Bubble!")
            }
        }
        bubbleManager.removeBubble(bubbleId)
        messenger.setCacheDirty()
    }

    @Subcommand("kick")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun kick(sender: Player, @Flags("bubble-owned") bubble: Bubble, @Single target: OnlinePlayer) {
        val player: Player = target.player
        if (player.uniqueId == sender.uniqueId)
            throw ChattoreException("You cannot kick yourself!")

        bubble.removePlayer(player.uniqueId)
        messenger.setCacheDirty()
        player.sendInfo("You were kicked out of the Chat-Bubble!")
        bubble.players.forEach { uuid ->
            val member = proxy.getPlayer(uuid).orElse(null) ?: return@forEach
            if (member == sender) {
                member.sendInfo("You kicked ${player.username} from the Chat-Bubble!")
            } else {
                member.sendInfo("${sender.username} kicked ${player.username} from your Chat-Bubble!")
            }
        }
    }

    @Subcommand("setPrivate")
    @CommandCompletion("true|false")
    fun setPrivate(sender: Player, @Flags("bubble-owned") bubble: Bubble, boolean: Boolean) {
        bubble.isPrivate = boolean
        bubble.players.forEach { uuid ->
            val member = proxy.getPlayer(uuid).orElse(null) ?: return@forEach
            if (member == sender) {
                member.sendInfo("The Chat-Bubble is now ${if (boolean) "private" else "public"}!")
            } else {
                member.sendInfo("${sender.username} set the Chat-Bubble to ${if (boolean) "private" else "public"}!")
            }
        }
    }

    @Subcommand("list")
    fun list(sender: Player) {
        if (bubbleManager.getBubbles().isEmpty()) {
            sender.sendInfo("There are currently no Chat-Bubbles!")
            return
        }
        sender.sendRichMessage("<yellow>Bubbles:</yellow>")
        for (bubble in bubbleManager.getBubbles().values) {
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
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    @CommandPermission("chattore.bubble.manage")
    fun forcedelete(sender: Player, @Single target: OnlinePlayer) {
        val player: Player = target.player
        val bubble = bubbleManager.getBubbleByPlayer(player)
            ?: throw ChattoreException("Target is not in a Chat-Bubble!")
        bubble.players.forEach { uuid ->
            proxy.getPlayer(uuid).orElse(null)?.sendInfo("The Chat-Bubble was burst by ${sender.username}!")
        }
        val bubbleId = bubbleManager.getBubbleId(player.uniqueId)!!
        bubbleManager.removeBubble(bubbleId)
        messenger.setCacheDirty()
        sender.sendInfo("You successfully burst ${player.username}'s Chat-Bubble!")
    }

    @CommandAlias("shout")
    fun shout(sender: Player, message: String) {
        messenger.broadcastChatMessage(sender.currentServer.get().serverInfo.name, sender, message)
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
}

data class Bubble(
    val owner: UUID,
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
    private val bubbles: MutableMap<Int, Bubble> = mutableMapOf()
    private var id: Int = 0

    fun getBubbles(): Map<Int, Bubble> {
        return bubbles
    }

    fun createBubble(player: UUID) {
        bubbles[id] = Bubble(player, mutableSetOf(player), mutableSetOf(), false)
        id += 1
    }

    fun removeBubble(id: Int) {
        bubbles.remove(id)
    }

    fun getBubbleId(player: UUID): Int? {
        return bubbles.entries.firstOrNull { it.value.players.contains(player) }?.key
    }

    fun getBubble(id: Int): Bubble? {
        return bubbles[id]
    }

    fun getBubbleByPlayer(player: Player): Bubble? {
        return bubbles.entries.firstOrNull { it.value.players.contains(player.uniqueId) }?.value
    }
}
