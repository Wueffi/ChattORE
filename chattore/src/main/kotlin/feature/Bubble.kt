package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandHelp
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.*
import co.aikar.commands.velocity.contexts.OnlinePlayer
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.openredstone.chattore.*
import java.util.*

private val ShowGlobalChatInBubble = Setting<Boolean>("showGlobalChatInBubble")
private const val BUBBLE_OWNED = "bubble-owned"

fun PluginScope.createBubbleFeature(
    messenger: Messenger,
    database: Storage,
    chatConfirmations: ChatConfirmations,
    formatConfig: FormatConfig,
): BubbleManager {
    val bubbleManager = BubbleManager()
    commandManager.apply {
        commandContexts.registerIssuerOnlyContext(Bubble::class.java) { ctx ->
            val sender = ctx.sender as? Player
                ?: throw InvalidCommandArgument("This command can only be used by players!")
            val bubble = bubbleManager.getBubbleByPlayer(sender)
                ?: throw InvalidCommandArgument("You are not in a bubble!")
            if (ctx.hasFlag(BUBBLE_OWNED) && bubble.owner != sender.uniqueId) {
                val ownerName = proxy.playerOrNull(bubble.owner)?.username ?: bubble.owner.toString()
                throw InvalidCommandArgument(
                    "You must be the owner of the bubble to use this command. (Current owner: $ownerName)",
                )
            }
            bubble
        }
        commandCompletions.registerStaticCompletion("boolean", arrayOf("true", "false"))
        commandCompletions.setDefaultCompletion("boolean", Boolean::class.java)
        commandCompletions.setDefaultCompletion("players", OnlinePlayer::class.java)
    }
    registerCommands(BubbleCommand(messenger, proxy, database, bubbleManager, chatConfirmations, formatConfig))
    return bubbleManager
}

@CommandAlias("bubble|bb")
@CommandPermission("chattore.bubble")
private class BubbleCommand(
    private val messenger: Messenger,
    private val proxy: ProxyServer,
    private val database: Storage,
    private val bubbleManager: BubbleManager,
    private val chatConfirmations: ChatConfirmations,
    private val formatConfig: FormatConfig,
) : BaseCommand() {

    @CatchUnknown
    @HelpCommand
    @Subcommand("help")
    fun help(sender: CommandSource, help: CommandHelp) {
        sender.sendInfoMM("<b><gold>Bubble help")
        help.showHelp()
    }

    @Subcommand("create|blow")
    @Description("Create (\"blow\") a bubble")
    fun create(sender: Player) {
        if (bubbleManager.getBubbleByPlayer(sender) != null)
            throw ChattoreException("You are already in a bubble!")
        bubbleManager.createBubble(sender.uniqueId)
        addExcluded(sender.uniqueId)
        sender.sendInfo("Bubble created.")
    }

    @Subcommand("invite")
    @Description("Invite a player to your bubble (if it is private)")
    fun invite(sender: Player, bubble: Bubble, target: OnlinePlayer) {
        val player = target.player
        if (player.uniqueId in bubble.invitedPlayers)
            throw ChattoreException("${player.username} is already invited!")
        if (player.uniqueId in bubble.players)
            throw ChattoreException("${player.username} is already in the bubble!")

        bubble.invitedPlayers.add(player.uniqueId)
        sender.sendInfo("Invited ${player.username}.")
        player.sendInfoC(
            "${sender.username} invited you to their bubble. ".toComponent().append(bubble.joinButton(proxy)),
        )
    }

    @Subcommand("join")
    @Description("Join a player's bubble")
    fun join(sender: Player, target: OnlinePlayer) {
        if (bubbleManager.getBubbleByPlayer(sender) != null)
            throw ChattoreException("You are already in a bubble!")

        val player = target.player
        val bubble = bubbleManager.getBubbleByPlayer(player)
            ?: throw ChattoreException("${player.username} is not in a bubble!")

        if (bubble.isPrivate && sender.uniqueId !in bubble.invitedPlayers && !sender.hasBubblePrivilege)
            throw ChattoreException("You are not invited to the bubble!")

        bubble.invitedPlayers.remove(sender.uniqueId)
        bubble.players.add(sender.uniqueId)
        addExcluded(sender.uniqueId)
        bubble.sendInfos(
            sender,
            "You joined ${player.username}'s bubble.",
            "${sender.username} joined the bubble.",
        )
    }

    @Subcommand("leave")
    @Description("Leave your current bubble")
    fun leave(sender: Player, bubble: Bubble) {
        bubble.players.remove(sender.uniqueId)
        messenger.excludedFromGlobalChat.remove(sender.uniqueId)
        sender.sendInfo("You left the bubble.")
        if (bubble.players.isEmpty()) {
            bubbleManager.removeBubble(bubble)
            return
        }
        bubble.broadcastInfo("${sender.username} left the bubble.")
        if (bubble.owner == sender.uniqueId) {
            val newOwner = bubble.players.first()
            bubble.owner = newOwner
            proxy.playerOrNull(newOwner)?.sendInfo("You are now the owner of the bubble.")
        }
    }

    @Subcommand("delete|pop")
    @Description("Delete (\"pop\") your own bubble")
    fun delete(sender: Player, @Flags(BUBBLE_OWNED) bubble: Bubble) {
        bubble.sendInfos(
            sender,
            "You popped the bubble.",
            "${sender.username} popped the bubble.",
        )
        messenger.excludedFromGlobalChat.removeAll(bubble.players)
        bubbleManager.removeBubble(bubble)
    }

    @Subcommand("kick")
    @Description("Kick a player from your own bubble")
    fun kick(sender: Player, @Flags(BUBBLE_OWNED) bubble: Bubble, target: OnlinePlayer) {
        val player = target.player
        if (player.uniqueId == sender.uniqueId)
            throw ChattoreException("You cannot kick yourself!")

        bubble.players.remove(player.uniqueId)
        messenger.excludedFromGlobalChat.remove(player.uniqueId)
        player.sendInfo("You were kicked out of the bubble.")
        bubble.sendInfos(
            sender,
            "You kicked ${player.username} from the bubble.",
            "${sender.username} kicked ${player.username} from the bubble.",
        )
    }

    @Subcommand("setprivate")
    @Description("Set the visibility of your own bubble")
    fun setPrivate(sender: Player, @Flags(BUBBLE_OWNED) bubble: Bubble, isPrivate: Boolean) {
        bubble.isPrivate = isPrivate
        val visibility = if (isPrivate) "private" else "public"
        bubble.sendInfos(
            sender,
            "The bubble is now $visibility.",
            "${sender.username} set the bubble to $visibility.",
        )
    }

    private fun Player.canSee(bubble: Bubble) = hasBubblePrivilege
        || !bubble.isPrivate || uniqueId in bubble.players || uniqueId in bubble.invitedPlayers

    private val Player.hasBubblePrivilege: Boolean get() = hasPermission("chattore.bubble.manage")

    @Subcommand("list")
    @Description("List all bubbles")
    fun list(sender: Player) {
        val bubbles = bubbleManager.bubbles.filter { sender.canSee(it) }
        if (bubbles.isEmpty()) {
            sender.sendInfo("There are currently no bubbles.")
            return
        }
        sender.sendRichMessage("<yellow>Bubbles:</yellow>")
        for (bubble in bubbles) {
            val info = if (sender.uniqueId in bubble.players) {
                "<gold>Your current bubble".render()
            } else {
                bubble.joinButton(proxy)
            }
            sender.sendMessage(
                Component.textOfChildren(
                    Component.text(bubble.playersString(proxy)),
                    " <gray>|</gray> ".render(),
                    info,
                )
            )
        }
    }

    @Subcommand("burst")
    @CommandPermission("chattore.bubble.manage")
    @Description("Burst (delete) someone's bubble")
    fun burst(sender: Player, target: OnlinePlayer) {
        val player = target.player
        val bubble = bubbleManager.getBubbleByPlayer(player)
            ?: throw ChattoreException("${player.username} is not in a bubble!")
        bubble.broadcastInfo("The bubble was burst by ${sender.username}.")
        messenger.excludedFromGlobalChat.removeAll(bubble.players)
        bubbleManager.removeBubble(bubble)
        sender.sendInfo("You burst ${player.username}'s bubble.")
    }

    @Subcommand("showglobalchat|sgc")
    @Description("Control the visibility of global chat when in a bubble")
    fun showGlobalChat(sender: Player, showGlobalChat: Boolean) {
        database.setSetting(ShowGlobalChatInBubble, sender.uniqueId, showGlobalChat)
        if (showGlobalChat) {
            messenger.excludedFromGlobalChat.remove(sender.uniqueId)
        } else if (bubbleManager.getBubbleByPlayer(sender) != null) {
            messenger.excludedFromGlobalChat.add(sender.uniqueId)
        }
        sender.sendInfo(
            if (showGlobalChat) "You will now see global chat in bubbles."
            else "You will no longer see global chat in bubbles."
        )
    }

    @CommandAlias("shout")
    @Description("Send a message to global chat when in a bubble")
    fun shout(sender: Player, message: String) {
        chatConfirmations.submit(sender, message) {
            messenger.broadcastChatMessage(sender, message)
            if (sender.uniqueId in messenger.excludedFromGlobalChat) {
                sender.sendMessage(
                    formatConfig.shoutPrefix.render().append(space())
                        .append(messenger.formatChatMessage(message, sender))
                )
            }
        }
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

    private fun addExcluded(uuid: UUID) {
        if (database.getSetting(ShowGlobalChatInBubble, uuid) != true) {
            messenger.excludedFromGlobalChat.add(uuid)
        }
    }
}

class Bubble(
    var owner: UUID,
    val players: MutableSet<UUID>,
    val invitedPlayers: MutableSet<UUID>,
    var isPrivate: Boolean,
) {
    fun playersString(proxy: ProxyServer) = players
        .mapNotNull { uuid -> proxy.playerOrNull(uuid)?.username }
        .joinToString(", ")

    fun formatInfo(proxy: ProxyServer) =
        HoverEvent.showText(Component.text("Bubble: ${playersString(proxy)}"))

    fun joinButton(proxy: ProxyServer): Component {
        val ownerName = proxy.playerOrNull(owner)?.username ?: owner.toString()
        return "<gray>[</gray><green>Join</green><gray>]</gray>".render()
            .clickEvent(ClickEvent.runCommand("/bubble join $ownerName"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to join bubble")))
    }
}

class BubbleManager {
    private val _bubbles: MutableList<Bubble> = mutableListOf()
    val bubbles: List<Bubble> get() = _bubbles

    fun createBubble(player: UUID) {
        _bubbles.add(Bubble(player, mutableSetOf(player), mutableSetOf(), false))
    }

    fun removeBubble(bubble: Bubble) {
        _bubbles.remove(bubble)
    }

    fun getBubbleByPlayer(player: Player): Bubble? = _bubbles.firstOrNull { player.uniqueId in it.players }
}
