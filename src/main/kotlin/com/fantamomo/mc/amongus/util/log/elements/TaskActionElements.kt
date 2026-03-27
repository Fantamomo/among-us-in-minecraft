package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object TaskActionElements {
    class TaskCompleted(val player: UUID, val task: String) : IdActionElement("task_completed") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }

    class TaskStepCompleted(val player: UUID, val task: String, val step: Int) : IdActionElement("task_step_completed") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
            put("step", step)
        }
    }

    class TaskFailed(val player: UUID, val task: String) : IdActionElement("task_failed") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }

    class TaskStarted(val player: UUID, val task: String) : IdActionElement("task_started") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }

    class TaskAssigned(val player: UUID, val task: String) : IdActionElement("task_assigned") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }

    class TaskUnassigned(val player: UUID, val task: String) : IdActionElement("task_unassigned") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }
}