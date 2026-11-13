package com.fantamomo.mc.amongus.advancement

import kotlinx.serialization.json.JsonElement
import net.kyori.adventure.text.Component
import java.util.*

interface AssignedAdvancement<A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> {
    val definition: A
    val player: UUID

    val name: Component
        get() = definition.name
    val description: Component
        get() = definition.description
    val descriptionOther: Component
        get() = definition.descriptionOther

    val isCompleted: Boolean

    fun toJson(): JsonElement
}