package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object MorphActionElements {
    class StartMorph(val player: UUID, val target: UUID) : IdActionElement("start_morph") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("target", target.toString())
        }
    }
    class EndMorph(val player: UUID) : IdActionElement("end_morph") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
        }
    }
}