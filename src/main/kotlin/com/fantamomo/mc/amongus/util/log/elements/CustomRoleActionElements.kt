package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object CustomRoleActionElements {
    class SnitchOneTaskLeft(val player: UUID) : IdActionElement("snitch_one_task_left") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
        }
    }

    class SnitchFinishedTasks(val player: UUID) : IdActionElement("snitch_finished_tasks") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
        }
    }

    class ExecutionerTargetSelected(val player: UUID, val target: UUID?) : IdActionElement("executioner_target_selected") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("target", target?.toString())
        }
    }
}