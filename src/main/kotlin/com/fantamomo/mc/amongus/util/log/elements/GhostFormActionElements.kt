package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object GhostFormActionElements {
    class Enter(val player: UUID) : IdActionElement("enter_ghost_form") {
        override fun toJson()= buildJsonObject {
            put("player", player.toString())
        }
    }

    class Exit(val player: UUID) : IdActionElement("exit_ghost_form") {
        override fun toJson()= buildJsonObject {
            put("player", player.toString())
        }
    }
}