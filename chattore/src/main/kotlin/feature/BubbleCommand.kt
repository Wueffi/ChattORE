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

fun PluginScope.createBubbleFeature(messenger: Messenger, formatConfig: FormatConfig, userCache: UserCache) {
    registerCommands(BubbleCommand(messenger, proxy, formatConfig, userCache))
}

@CommandAlias("bubble|bb")
@CommandPermission("chattore.bubble")
private class BubbleCommand(
    private val messenger: Messenger,
    private val proxy: ProxyServer,
    private val formatConfig: FormatConfig,
    private var userCache: UserCache,
) : BaseCommand() {
    @Subcommand("create")
    @CommandAlias("blow")
    fun create(sender: Player) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble != null) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                "message" toC Component.text("You are already in a Chat-Bubble!")
            )
            return
        }
        messenger.bubbleManager.createBubble(sender.uniqueId)
        messenger.setCacheDirty()
        sender.sendRichMessage(
            formatConfig.bubbleInfoMessage,
            "message" toC Component.text("Successfully created Chat-Bubble!")
        )
    }

    @Subcommand("invite")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun invite(sender: Player, @Single target: String) {
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble?.invitePlayer(player.uniqueId) == true) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                "message" toC Component.text("Successfully invited ${player.username}!")
            )
            player.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("${sender.username} invited you to their Chat-Bubble!")
            )
        } else {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("Could not invite ${player.username}!")
            )
        }
    }

    @Subcommand("join")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun join(sender: Player, @Single target: String) {
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }
        val senderBubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (senderBubble != null) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                "message" toC Component.text("You are already in a Chat-Bubble!")
            )
            return
        }
        val bubble = messenger.bubbleManager.getBubbleByPlayer(player)
        if (bubble?.addPlayer(sender.uniqueId) == true) {
            messenger.setCacheDirty()
            bubble.players.forEach { uuid ->
                val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

                if (target == sender) {
                    target.sendRichMessage(
                        formatConfig.bubbleInfoMessage,
                            "message" toC Component.text("You successfully joined ${player.username}'s Chat-Bubble!")
                    )
                } else {
                    target.sendRichMessage(
                        formatConfig.bubbleInfoMessage,
                                "message" toC Component.text("${sender.username} joined your Chat-Bubble!")
                    )
                }
            }
        } else {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You were not invited to ${player.username}'s Chat-Bubble!")
            )
        }
    }

    @Subcommand("leave")
    fun leave(sender: Player) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble == null) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You are not in a Chat-Bubble!")
            )
            return
        }
        val bubbleId = messenger.bubbleManager.getBubbleId(sender.uniqueId)!!
        if (bubble.removePlayer(sender.uniqueId)) {
            messenger.setCacheDirty()
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You successfully left the Chat-Bubble!")
            )
            if (bubble.players.isEmpty()) {
                messenger.bubbleManager.removeBubble(bubbleId)
                return
            }
            bubble.players.forEach { uuid ->
                val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

                target.sendRichMessage(
                    formatConfig.bubbleInfoMessage,
                        "message" toC Component.text("${sender.username} left your Chat-Bubble!")
                )
            }
        } else {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("Could not leave Chat-Bubble!")
            )
        }
    }

    @Subcommand("delete")
    @CommandAlias("pop")
    fun delete(sender: Player) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble == null) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You are not in a Chat-Bubble!")
            )
            return
        } else if (bubble.players.first() != sender.uniqueId) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You cannot pop this Chat-Bubble!")
            )
            return
        }
        val bubbleId = messenger.bubbleManager.getBubbleId(sender.uniqueId)!!
        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

            if (target == sender) {
                target.sendRichMessage(
                    formatConfig.bubbleInfoMessage,
                        "message" toC Component.text("You popped the Chat-Bubble!")
                )
            } else {
                target.sendRichMessage(
                    formatConfig.bubbleInfoMessage,
                        "message" toC Component.text("${sender.username} popped your Chat-Bubble!")
                )
            }
        }
        messenger.bubbleManager.removeBubble(bubbleId)
        messenger.setCacheDirty()
    }

    @Subcommand("kick")
    @CommandCompletion("@${UserCache.COMPLETION_USERNAMES}")
    fun kick(sender: Player, @Single target: String) {
        val targetUuid = userCache.uuidOrNull(target)
            ?: throw ChattoreException("We do not recognize that user!")
        val player: Player = proxy.getPlayer(targetUuid)
            .orElseThrow { ChattoreException("User is not online!") }
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble == null) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You are not in a Chat-Bubble!")
            )
            return
        } else if (bubble.players.first() != sender.uniqueId) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You cannot kick players from this Chat-Bubble!")
            )
            return
        }
        if (bubble.removePlayer(player.uniqueId)) {
            messenger.setCacheDirty()
            player.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You were kicked out of the Chat-Bubble!")
            )
            bubble.players.forEach { uuid ->
                val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

                if (target == sender) {
                    target.sendRichMessage(
                        formatConfig.bubbleInfoMessage,
                                "message" toC Component.text("You kicked ${player.username} from the Chat-Bubble!")
                    )
                } else {
                    target.sendRichMessage(
                        formatConfig.bubbleInfoMessage,
                                "message" toC Component.text("${sender.username} kicked ${player.username} from your Chat-Bubble!")
                    )
                }
            }
        } else {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("Could not leave Chat-Bubble!")
            )
        }
    }

    @Subcommand("setPrivate")
    @CommandCompletion("true|false")
    fun setPrivate(sender: Player, boolean: Boolean) {
        val bubble = messenger.bubbleManager.getBubbleByPlayer(sender)
        if (bubble == null) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You are not in a Chat-Bubble!")
            )
            return
        } else if (bubble.players.first() != sender.uniqueId) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You cannot change the visibility of this Chat-Bubble!")
            )
            return
        }

        bubble.visibility = boolean

        bubble.players.forEach { uuid ->
            val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach

            if (target == sender) {
                target.sendRichMessage(
                    formatConfig.bubbleInfoMessage,
                        "message" toC Component.text("You set the visibility of the Chat-Bubble to $boolean!")
                )
            } else {
                target.sendRichMessage(
                    formatConfig.bubbleInfoMessage,
                        "message" toC Component.text("${sender.username} set the visibility of the Chat-Bubble to $boolean!")
                )
            }
        }
    }

    @Subcommand("list")
    fun list(sender: Player) {
        if (messenger.bubbleManager.getBubbles().isEmpty()) {
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                "message" toC Component.text("There are currently no Chat-Bubbles!")
            )
            return
        }

        sender.sendRichMessage(formatConfig.bubbleHeading)

        for (bubble in messenger.bubbleManager.getBubbles().values) {
            val names = mutableListOf<String>()

            bubble.players.forEach { uuid ->
                val target = proxy.getPlayer(uuid).orElse(null) ?: return@forEach
                names.add(target.username)
            }

            val playersString = names.joinToString(", ")

            val firstPlayer = bubble.players.firstOrNull()?.let { uuid ->
                proxy.getPlayer(uuid).orElse(null)?.username
            }

            val string = "$playersString <gray>|</gray> <gray>[</gray><green>Join</green><gray>]</gray>"

            val component = if (firstPlayer != null) {
                Component.text(string)
                    .clickEvent(
                        ClickEvent.suggestCommand("/bubble join $firstPlayer")
                    )
                    .hoverEvent(
                        HoverEvent.showText(Component.text("Click to join bubble"))
                    )
            } else {
                Component.text(string)
            }

            sender.sendRichMessage(
                "<component>",
                "component" toC component
            )
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
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("Target is not in a Chat-Bubble!")
            )
            return
        }

        val bubbleId = messenger.bubbleManager.getBubbleId(player.uniqueId)!!
        messenger.bubbleManager.removeBubble(bubbleId)
        messenger.setCacheDirty()

        sender.sendRichMessage(
            formatConfig.bubbleInfoMessage,
                "message" toC Component.text("You successfully burst ${player.username}'s Chat-Bubble!")
        )
    }

    @CommandAlias("shout")
    fun shout(sender: Player, message: String) {
        if (message != "") messenger.broadcastChatMessage(sender.currentServer.get().serverInfo.name, sender, message)
    }

    @CommandAlias("seeglobalchat|gc")
    @CommandCompletion("true|false")
    fun seeGlobalChat(sender: Player, boolean: Boolean) {
        if (boolean) {
            messenger.addGlobalOverride(sender.uniqueId)
            sender.sendRichMessage(
                formatConfig.bubbleInfoMessage,
                    "message" toC Component.text("You will now see global chat inside your Chat-Bubble!")
            )
            return
        }
        messenger.removeGlobalOverride(sender.uniqueId)
        sender.sendRichMessage(
            formatConfig.bubbleInfoMessage,
                "message" toC Component.text("You won't see global chat inside your Chat-Bubble anymore!")
        )
    }
}
