package com.fantamomo.mc.amongus.util.log

import kotlinx.serialization.json.JsonElement

open class IdActionElement(override val action: String) : ActionElement {
    override fun toJson(): JsonElement? = null
}