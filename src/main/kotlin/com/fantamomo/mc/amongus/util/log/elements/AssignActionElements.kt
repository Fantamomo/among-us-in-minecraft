package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object AssignActionElements {

    class AssignRole(val player: UUID, val role: String) : IdActionElement("assign_role") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("role", role)
        }
    }

    class AssignModification(val player: UUID, val modification: String) : IdActionElement("assign_modification") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("modification", modification)
        }
    }
}