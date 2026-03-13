package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

object TaskActionElements {
    class TaskCompleted(val player: Uuid, val task: String) : IdActionElement("task_completed") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }

    class TaskStepCompleted(val player: Uuid, val task: String, val step: Int) : IdActionElement("task_step_completed") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
            put("step", step)
        }
    }

    class TaskFailed(val player: Uuid, val task: String) : IdActionElement("task_failed") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }

    class TaskStarted(val player: Uuid, val task: String) : IdActionElement("task_started") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("task", task)
        }
    }
}