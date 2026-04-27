package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Single
import co.aikar.commands.annotation.Subcommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.openredstone.chattore.*
import java.util.UUID

val seeGlobalChatEnabled = Setting<Boolean>("seeGlobalChat")

fun PluginScope.createBubbleFeature(messenger: Messenger, userCache: UserCache, database: Storage) {
    registerCommands(BubbleCommand(messenger, proxy, userCache, database))
}

@CommandAlias("bubble|bb")
@CommandPermission("chattore.bubble")
private class BubbleCommand(
    private val messenger: Messenger,
    private val proxy: ProxyServer,
    private var userCache: UserCache,
    private var database: Storage
) : BaseCommand() {

    private val Player.seeGlobalChat: Boolean get() = database.getSetting(seeGlobalChatEnabled, uniqueId) ?: false

    @Subcommand("create")
    @CommandAlias("blow")
    fun create(sender: Player) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble != null) throw ChattoreException("You are already in a Chat-Bubble!")
        messenger.bubbleManager.createBubble(sender.uniqueId)
        messenger.setCacheDirty()
        sender.sendInfo("Successfully created Chat-Bubble!")
    }

    @Subcommand("invite")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun invite(sender: Player, @Single target: String) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
            ?: throw ChattoreException("You are not in a Chat-Bubble!")
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }

        if (bubble.isInvited(player.uniqueId)) {
            sender.sendInfo("${player.username} is already invited!")
            return
        } else if (bubble.containsPlayer(player.uniqueId)) {
            sender.sendInfo("${player.username} is already in the Chat-Bubble!")
            return
        }

        bubble.invitePlayer(player.uniqueId)
        sender.sendInfo("Successfully invited ${player.username}!")
        player.sendInfo("${sender.username} invited you to their Chat-Bubble!")

    }

    @Subcommand("join")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun join(sender: Player, @Single target: String) {
        val senderBubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (senderBubble != null) {
            sender.sendInfo("You are already in a Chat-Bubble!")
            return
        }
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }
        val bubble = messenger.bubbleManager.getBubbleByPlayer(player)
            ?: throw ChattoreException("Target is not in a Chat-Bubble!")
        
        if (!bubble.isInvited(player.uniqueId) && bubble.isPrivate) {
            sender.sendInfo("You were not invited to the Chat-Bubble!")
            return
        }

        bubble.addPlayer(sender.uniqueId)
        messenger.setCacheDirty()
        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

            if (target == sender) {
                target.sendInfo("You successfully joined ${player.username}'s Chat-Bubble!")
            } else {
                target.sendInfo("${sender.username} joined your Chat-Bubble!")
            }
        }
    }

    @Subcommand("leave")
    fun leave(sender: Player) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
            ?: throw ChattoreException("You are not in a Chat-Bubble!")
        val bubbleId = messenger.bubbleManager.getBubbleId(sender.uniqueId)!!
        bubble.removePlayer(sender.uniqueId)
        messenger.setCacheDirty()
        sender.sendInfo("You successfully left the Chat-Bubble!")
        if (bubble.players.isEmpty()) {
            messenger.bubbleManager.removeBubble(bubbleId)
            return
        }
        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach
            target.sendInfo("${sender.username} left your Chat-Bubble!")
        }
    }

    @Subcommand("delete")
    @CommandAlias("pop")
    fun delete(sender: Player) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
            ?: throw ChattoreException("You are not in a Chat-Bubble!")
        
        if (!bubble.isOwner(sender.uniqueId)) {
            val ownerName = proxy.getPlayer(bubble.owner)
            sender.sendInfo("You are not the owner of this Chat-Bubble! (${ownerName} is.)")
            return
        }
        val bubbleId = messenger.bubbleManager.getBubbleId(sender.uniqueId)!!
        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

            if (target == sender) {
                target.sendInfo("You popped the Chat-Bubble!")
            } else {
                target.sendInfo("${sender.username} popped your Chat-Bubble!")
            }
        }
        messenger.bubbleManager.removeBubble(bubbleId)
        messenger.setCacheDirty()
    }

    @Subcommand("kick")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun kick(sender: Player, @Single target: String) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
            ?: throw ChattoreException("You are not in a Chat-Bubble!")
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }

        if (player.uniqueId == sender.uniqueId) {
            sender.sendInfo("You cannot kick yourself!")
            return
        } else if (!bubble.isOwner(sender.uniqueId)) {
            val ownerName = proxy.getPlayer(bubble.owner)
            sender.sendInfo("You are not the owner of this Chat-Bubble! (${ownerName} is.)")
            return
        }

        bubble.removePlayer(player.uniqueId)
        messenger.setCacheDirty()
        player.sendInfo("You were kicked out of the Chat-Bubble!")
        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

            if (target == sender) {
                target.sendInfo("You kicked ${player.username} from the Chat-Bubble!")
            } else {
                target.sendInfo("${sender.username} kicked ${player.username} from your Chat-Bubble!")
            }
        }
    }

    @Subcommand("setPrivate")
    @CommandCompletion("true|false")
    fun setPrivate(sender: Player, boolean: Boolean) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
            ?: throw ChattoreException("You are not in a Chat-Bubble!")
        if (!bubble.isOwner(sender.uniqueId)) {
            val ownerName = proxy.getPlayer(bubble.owner)
            sender.sendInfo("You are not the creator of this Chat-Bubble! (${ownerName} is.)")
            return
        }

        bubble.isPrivate = boolean

        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

            if (target == sender) {
                target.sendInfo("The Chat-Bubble is now ${if (boolean) "private" else "public"}!")
            } else {
                target.sendInfo("${sender.username} set the Chat-Bubble to ${if (boolean) "private" else "public"}!")
            }
        }
    }

    @Subcommand("list")
    fun list(sender: Player) {
        if (messenger.bubbleManager.getBubbles().isEmpty()) {
            sender.sendInfo("There are currently no Chat-Bubbles!")
            return
        }

        sender.sendRichMessage("<yellow>Bubbles:</yellow>")

        for (bubble in messenger.bubbleManager.getBubbles().values) {
            val playersString = bubble.players
                .mapNotNull { uuid -> proxy.getPlayer(uuid).orElse(null)?.username }
                .joinToString(", ")

            val firstPlayer = bubble.players.firstOrNull()?.let { uuid ->
                proxy.getPlayer(uuid).orElse(null)?.username
            }

            val string = "$playersString <gray>|</gray> <gray>[</gray><green>Join</green><gray>]</gray>"

            val component = Component.text(string)
                .clickEvent(
                    ClickEvent.suggestCommand("/bubble join $firstPlayer")
                )
                .hoverEvent(
                    HoverEvent.showText(Component.text("Click to join bubble"))
                )
            sender.sendMessage(component)
        }
    }

    @Subcommand("forcedelete")
    @CommandAlias("burst")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    @CommandPermission("chattore.bubble.manage")
    fun forcedelete(sender: Player, @Single target: String) {
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }
        val bubble = messenger.bubbleManager.getBubbleByPlayer(player)
        if (bubble == null) {
            sender.sendInfo("Target is not in a Chat-Bubble!")
            return
        }

        val bubbleId = messenger.bubbleManager.getBubbleId(player.uniqueId)!!
        messenger.bubbleManager.removeBubble(bubbleId)
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
        if (boolean) {
            sender.sendInfo("You will now see global chat inside your Chat-Bubble!")
            return
        }
        sender.sendInfo("You won't see global chat inside your Chat-Bubble anymore!")
    }
}

data class Bubble(
    val owner: UUID,
    val players: MutableSet<UUID>,
    val invitedPlayers: MutableSet<UUID>,
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
    val bubbles: MutableMap<Int, Bubble> = mutableMapOf()
    var id: Int = 0

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
        return bubbles.entries.firstOrNull{it.value.players.contains(player)}?.key
    }

    fun getBubble(id: Int): Bubble? {
        return bubbles[id]
    }

    fun getBubbleByPlayer(player: Player): Bubble? {
        return bubbles.entries.firstOrNull{it.value.players.contains(player.uniqueId)}?.value
    }
}