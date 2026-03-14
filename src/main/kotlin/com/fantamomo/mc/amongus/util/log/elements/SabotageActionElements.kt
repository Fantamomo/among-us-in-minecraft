package com.fantamomo.mc.amongus.util.log.elements

import com.fantamomo.mc.amongus.util.log.IdActionElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

object SabotageActionElements {
    class Start(val by: UUID?, val sabotage: String) : IdActionElement("start_sabotage") {
        override fun toJson() = buildJsonObject {
            put("by", by?.toString())
            put("sabotage", sabotage)
        }
    }

    class End(val sabotage: String, val fixed: Boolean) : IdActionElement("end_sabotage") {
        override fun toJson() = buildJsonObject {
            put("sabotage", sabotage)
            put("fixed", fixed)
        }
    }
}