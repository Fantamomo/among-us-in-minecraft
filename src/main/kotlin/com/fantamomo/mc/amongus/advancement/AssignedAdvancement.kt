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

    /**
     * Whether the advancement has been completed.
     *
     * (The system will save this state, no need to manually save it in [toJson])
     */
    var isCompleted: Boolean
        @Deprecated(
            message = "This is only the state, for completing advancements, call award()",
            replaceWith = ReplaceWith("this.award()"),
            level = DeprecationLevel.ERROR
        )
        set

    fun award() {
        AdvancementManager.awardAdvancement(this)
    }

    /**
     * The data to store for this advancement.
     *
     * Will be passed to [Advancement.createOf] if it exists and is not [kotlinx.serialization.json.JsonNull].
     *
     * Return [kotlinx.serialization.json.JsonNull] to indicate that there is no data to store
     */
    fun toJson(): JsonElement
}