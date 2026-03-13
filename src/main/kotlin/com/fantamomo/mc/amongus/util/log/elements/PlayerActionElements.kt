package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.Uuid

object PlayerActionElements {

    class PlayerJoin(val player: Uuid) : IdActionElement("player_join") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("player", player.toString())
        }
    }

    class PlayerDisconnect(val player: Uuid) : IdActionElement("player_disconnect") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("player", player.toString())
        }
    }

    class PlayerRejoin(val player: Uuid) : IdActionElement("player_rejoin") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("player", player.toString())
        }
    }

    class PlayerRemove(val player: Uuid) : IdActionElement("player_remove") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("player", player.toString())
        }
    }

    class PlayerLeave(val player: Uuid) : IdActionElement("player_leave") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("player", player.toString())
        }
    }

    class PlayerDeath(val player: Uuid, val reason: DeadReason) : IdActionElement("player_death") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("player", player.toString())
            if (reason !is DeadReason.Murdered) {
                val name = when (reason) {
                    DeadReason.Command -> "command"
                    DeadReason.Disconnected -> "disconnected"
                    DeadReason.Ejected -> "ejected"
                    DeadReason.Suicide -> "suicide"
                    DeadReason.Unknown -> "unknown"
                }
                put("reason", name)
            } else {
                putJsonObject("reason") {
                    put("type", "murdered")
                    put("killer", reason.murderer.uuid.toString())
                }
            }
        }
    }
}