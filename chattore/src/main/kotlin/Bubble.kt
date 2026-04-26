package org.openredstone.chattore

import com.velocitypowered.api.proxy.Player
import java.util.UUID

class Bubble(val players: MutableList<UUID>, val invitedPlayers: MutableList<UUID>, var visibility: Boolean) {
    fun addPlayer(uuid: UUID): Boolean {
        if (visibility) return players.add(uuid)
        return if (invitedPlayers.contains(uuid)) players.add(uuid) && invitedPlayers.remove(uuid)
        else false
    }

    fun removePlayer(uuid: UUID): Boolean {
        return players.remove(uuid)
    }

    fun invitePlayer(uuid: UUID): Boolean {
        return invitedPlayers.add(uuid)
    }

    fun uninvitePlayer(uuid: UUID): Boolean {
        return invitedPlayers.remove(uuid)
    }
}

val bubbles: MutableMap<Int, Bubble> = mutableMapOf()
var id: Int = 0

class BubbleManager() {
    fun getBubbles(): Map<Int, Bubble> {
        return bubbles
    }

    fun createBubble(player: UUID) {
        bubbles[id] = Bubble(mutableListOf(player), mutableListOf(), true)
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