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
) {
    val replyMap = ConcurrentHashMap<UUID, UUID>()
    registerCommands(
        Message(proxy, logger, messenger, replyMap, chatConfirmations),
        Reply(proxy, logger, messenger, replyMap, chatConfirmations),
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
) : BaseCommand() {
    @Default
    @Syntax("[target] <message>")
    fun default(sender: Player, recipient: OnlinePlayer, message: String) {
        val recipientUuid = recipient.player.uniqueId
        chatConfirmations.submit(sender, message) {
            val recipientPlayer = proxy.playerOrNull(recipientUuid)
                ?: throw ChattoreException("The person you're trying to message is no longer online!")
            sendMessage(logger, messenger, replyMap, sender, recipientPlayer, message)
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
) : BaseCommand() {
    @Default
    fun default(sender: Player, message: String) {
        val recipientUuid = replyMap[sender.uniqueId] ?: throw ChattoreException("You have no one to reply to!")
        chatConfirmations.submit(sender, message) {
            val recipient = proxy.playerOrNull(recipientUuid)
                ?: throw ChattoreException("The person you are trying to reply to is no longer online!")
            sendMessage(logger, messenger, replyMap, sender, recipient, message)
        }
    }
}

private fun sendMessage(
    logger: Logger,
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
    sender.sendRichMessage(
        "<gold>[</gold><red>me</red> <gold>-></gold> <red><recipient></red><gold>]</gold> <message>",
        "message" toC messenger.prepareChatMessage(message, sender),
        "recipient" toS recipient.username,
    )
    recipient.sendRichMessage(
        "<gold>[</gold><red><sender></red> <gold>-></gold> <red>me</red><gold>]</gold> <message>",
        "message" toC messenger.prepareChatMessage(message, sender),
        "sender" toS sender.username,
    )
    replyMap[recipient.uniqueId] = sender.uniqueId
    replyMap[sender.uniqueId] = recipient.uniqueId
}
