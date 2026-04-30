package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.Settings
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object GameActionElements {

    val Start = IdActionElement("game_start")

    val End = IdActionElement("game_end")

    val StartCountdown = IdActionElement("game_start_countdown")

    class StartCountdownAborted(val reason: Reason, val remaining: Int) :
        IdActionElement("countdown_aborted") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("reason", reason.name)
            put("remaining", remaining)
        }

        enum class Reason {
            SETTING_CHANGED,
            PLAYER_JOIN,
            HOST,
            UNKNOWN
        }
    }

    class PhaseChange(val old: GamePhase, val new: GamePhase) : IdActionElement("phase_change") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("old", old.name)
            put("new", new.name)
        }
    }

    class HostChange(val old: UUID?, val new: UUID?) : IdActionElement("host_change") {
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

    class SettingsChange(val id: String, val old: String, val new: String) : IdActionElement("settings_change") {
        override fun toJson(): JsonElement = buildJsonObject {
            put("id", id)
            put("old", old)
            put("new", new)
        }

        companion object {
            fun <T : Any> of(settings: Settings, key: SettingsKey<T, *>, new: T): SettingsChange {
                val current = key.type.stringRepresentation(settings[key])
                val new = key.type.stringRepresentation(new)
                return SettingsChange(key.key, current, new)
            }
        }
    }
}
