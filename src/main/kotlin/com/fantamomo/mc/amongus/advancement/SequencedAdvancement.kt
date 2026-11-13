package com.fantamomo.mc.amongus.advancement

import kotlinx.serialization.json.*
import java.util.*

abstract class SequencedAdvancement(override val id: String) :
    Advancement<SequencedAdvancement, SequencedAdvancement.AssignedSequencedAdvancement> {

    abstract val runner: AdvancementSequenceRunner

    override fun new(player: UUID) = AssignedSequencedAdvancement(this, player)

    override fun createOf(
        player: UUID,
        data: JsonElement
    ): AssignedSequencedAdvancement {
        val isCompleted = ((data as? JsonObject)?.get("is_completed") as? JsonPrimitive)?.booleanOrNull ?: false
        return AssignedSequencedAdvancement(this, player, isCompleted)
    }

    class AssignedSequencedAdvancement(
        override val definition: SequencedAdvancement,
        override val player: UUID,
        override var isCompleted: Boolean = false,
    ) : AssignedAdvancement<SequencedAdvancement, AssignedSequencedAdvancement> {
        override fun toJson() = buildJsonObject {
            put("is_completed", isCompleted)
        }
    }
}