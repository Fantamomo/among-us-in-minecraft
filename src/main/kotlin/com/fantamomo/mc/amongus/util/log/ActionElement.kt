package com.fantamomo.mc.amongus.util.log

import kotlinx.serialization.json.JsonElement

interface ActionElement {
    val action: String

    fun toJson(): JsonElement?
}