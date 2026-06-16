package org.openredstone.chattore.feature

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.space
import org.openredstone.chattore.*

// TODO: rename the key, requires a DB migration
private val CommandSpyEnabled = Setting<Boolean>("spy")
private val SocialSpyEnabled = Setting<Boolean>("socialSpy")

typealias Wiretap = (Component) -> Unit

fun PluginScope.createSpyingFeature(database: Storage, formatConfig: FormatConfig): Wiretap {
    fun spyAudience(spySetting: Setting<Boolean>) =
        proxy.all { it.hasChattorePrivilege && database.getSetting(spySetting, it.uniqueId) == true }

    val commandSpies = spyAudience(CommandSpyEnabled)
    val socialSpies = spyAudience(SocialSpyEnabled)
    registerCommands(SpyCommands(database))
    registerListeners(CommandListener(commandSpies))

    val spyPrefix = formatConfig.spyPrefix.render().append(space())
    return { secrets -> socialSpies.sendMessage(spyPrefix.append(secrets)) }
}

private class CommandListener(
    private val spies: Audience,
) {
    @Subscribe
    fun onCommandEvent(event: CommandExecuteEvent) {
        spies.sendRichMessage(
            "<gold><sender>: <message>",
            "message" toS event.command,
            "sender" toS ((event.commandSource as? Player)?.username ?: "Console"),
        )
    }
}

private class SpyCommands(
    private val database: Storage,
) : BaseCommand() {
    @CommandAlias("commandspy")
    @CommandPermission("chattore.commandspy")
    fun commandSpy(player: Player) {
        toggleSpy(player, CommandSpyEnabled, "Command spy")
    }

    @CommandAlias("socialspy")
    @CommandPermission("chattore.socialspy")
    fun socialSpy(player: Player) {
        toggleSpy(player, SocialSpyEnabled, "Social spy")
    }

    private fun toggleSpy(player: Player, spySetting: Setting<Boolean>, kind: String) {
        val newSetting = database.getSetting(spySetting, player.uniqueId) != true
        database.setSetting(spySetting, player.uniqueId, newSetting)
        player.sendInfo(
            if (newSetting) {
                "$kind enabled."
            } else {
                "$kind disabled."
            },
        )
    }
}
