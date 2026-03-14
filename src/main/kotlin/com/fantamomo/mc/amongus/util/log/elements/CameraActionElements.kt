package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object CameraActionElements {
    class JoinCamera(val player: UUID, val camera: String) : IdActionElement("join_camera") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("camera", camera)
        }
    }

    class SwitchCamera(val player: UUID, val old: String, val new: String) : IdActionElement("switch_camera") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("old", old)
            put("new", new)
        }
    }

    class LeaveCamera(val player: UUID, val camera: String) : IdActionElement("leave_camera") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("camera", camera)
        }
    }
}