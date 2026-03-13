package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

object GameActionElements {

    val Start = IdActionElement("game_start")

    val End = IdActionElement("game_end")

    val StartCooldown = IdActionElement("game_start_cooldown")

    class StartCooldownAborted(val reason: Reason, val remaining: Int) :
        IdActionElement("cooldown_aborted") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("reason", reason.name)
            put("remaining", remaining)
        }

        enum class Reason {
            SETTING_CHANGED,
            PLAYER_JOIN,
            UNKNOWN
        }
    }

    class PhaseChange(val old: GamePhase, val new: GamePhase) : IdActionElement("phase_change") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("old", old.name)
            put("new", new.name)
        }
    }

    class HostChange(val old: Uuid?, val new: Uuid?) : IdActionElement("host_change") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("old", old?.toString())
            put("new", new?.toString())
        }
    }

    class WinnerAnnouncement(val winner: Team) : IdActionElement("winner_announcement") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("winner", winner.name)
        }
    }
}
