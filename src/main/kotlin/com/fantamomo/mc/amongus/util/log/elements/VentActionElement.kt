package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.*

object VentActionElement {
    class Enter(val player: UUID, val ventGroup: Int, val location: Triple<Int, Int, Int>) : IdActionElement("enter_vent") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("ventGroup", ventGroup)
            putLocation("location", location)
        }
    }

    class Exit(val player: UUID, val ventGroup: Int, val location: Triple<Int, Int, Int>) : IdActionElement("exit_vent") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("ventGroup", ventGroup)
            putLocation("location", location)
        }
    }

    class Switch(val player: UUID, val ventGroup: Int, val from: Triple<Int, Int, Int>, val to: Triple<Int, Int, Int>) : IdActionElement("switch_vent") {
        override fun toJson() = buildJsonObject {
            put("player", player.toString())
            put("ventGroup", ventGroup)
            putLocation("from", from)
            putLocation("to", to)
        }
    }

    private fun JsonObjectBuilder.putLocation(id: String, location: Triple<Int, Int, Int>) {
        putJsonObject(id) {
            put("x", location.first)
            put("y", location.second)
            put("z", location.third)
        }
    }
}