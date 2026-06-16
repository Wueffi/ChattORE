package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Syntax
import co.aikar.commands.velocity.contexts.OnlinePlayer
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.openredstone.chattore.*
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun PluginScope.createMessageFeature(
    messenger: Messenger,
    chatConfirmations: ChatConfirmations,
    wiretap: Wiretap,
) {
    val replyMap = ConcurrentHashMap<UUID, UUID>()
    registerCommands(
        Message(proxy, logger, messenger, replyMap, chatConfirmations, wiretap),
        Reply(proxy, logger, messenger, replyMap, chatConfirmations, wiretap),
    )
}

@CommandAlias("m|pm|msg|message|vmsg|vmessage|whisper|tell")
@CommandPermission("chattore.message")
private class Message(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val messenger: Messenger,
    private val replyMap: ConcurrentHashMap<UUID, UUID>,
    private val chatConfirmations: ChatConfirmations,
    private val wiretap: Wiretap,
) : BaseCommand() {
    @Default
    @Syntax("[target] <message>")
    fun default(sender: Player, recipient: OnlinePlayer, message: String) {
        val recipientUuid = recipient.player.uniqueId
        chatConfirmations.submit(sender, message) {
            val recipientPlayer = proxy.playerOrNull(recipientUuid)
                ?: throw ChattoreException("The person you're trying to message is no longer online!")
            sendMessage(logger, wiretap, messenger, replyMap, sender, recipientPlayer, message)
        }
    }
}

@CommandAlias("r|reply")
@CommandPermission("chattore.message")
private class Reply(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val messenger: Messenger,
    private val replyMap: ConcurrentHashMap<UUID, UUID>,
    private val chatConfirmations: ChatConfirmations,
    private val wiretap: Wiretap,
) : BaseCommand() {
    @Default
    fun default(sender: Player, message: String) {
        val recipientUuid = replyMap[sender.uniqueId] ?: throw ChattoreException("You have no one to reply to!")
        chatConfirmations.submit(sender, message) {
            val recipient = proxy.playerOrNull(recipientUuid)
                ?: throw ChattoreException("The person you are trying to reply to is no longer online!")
            sendMessage(logger, wiretap, messenger, replyMap, sender, recipient, message)
        }
    }
}

private fun sendMessage(
    logger: Logger,
    wiretap: Wiretap,
    messenger: Messenger,
    replyMap: MutableMap<UUID, UUID>,
    sender: Player,
    recipient: Player,
    message: String,
) {
    logger.info(
        "${sender.username} (${sender.uniqueId}) -> " +
            "${recipient.username} (${recipient.uniqueId}): $message"
    )

    val preparedMessage = messenger.prepareChatMessage(message, sender)
    fun renderDM(senderName: String, recipientName: String) =
        "<gold>[</gold><red><sender></red> <gold>-></gold> <red><recipient></red><gold>]</gold> <message>".render(
            "sender" toS senderName, "recipient" toS recipientName, "message" toC preparedMessage,
        )

    sender.sendMessage(renderDM("me", recipient.username))
    recipient.sendMessage(renderDM(sender.username, "me"))
    wiretap(renderDM(sender.username, recipient.username))

    replyMap[recipient.uniqueId] = sender.uniqueId
    replyMap[sender.uniqueId] = recipient.uniqueId
}
