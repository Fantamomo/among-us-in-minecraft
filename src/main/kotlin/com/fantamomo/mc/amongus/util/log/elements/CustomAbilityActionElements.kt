package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object CustomAbilityActionElements {
    class ArsonistDouse(val arsonist: UUID, val target: UUID) : IdActionElement("arsonist_douse") {
        override fun toJson() = buildJsonObject {
            put("arsonist", arsonist.toString())
            put("target", target.toString())
        }
    }

    class CamouflageModeActivated(val player: UUID) : IdActionElement("camouflage_mode_activated") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
        }
    }

    class CannibalEatBody(val cannibal: UUID, val body: UUID) : IdActionElement("cannibal_eat_body") {
        override fun toJson() = buildJsonObject {
            put("cannibal", cannibal.toString())
            put("body", body.toString())
        }
    }

    class RevealTeam(val seer: UUID, val target: UUID) : IdActionElement("reveal_team") {
        override fun toJson() = buildJsonObject {
            put("seer", seer.toString())
            put("target", target.toString())
        }
    }
}