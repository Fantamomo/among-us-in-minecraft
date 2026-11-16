package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import kotlinx.serialization.json.*
import java.util.*

@ExperimentalAdvancementApi
abstract class SequencedAdvancement(override val id: String) :
    Advancement<SequencedAdvancement, SequencedAdvancement.AssignedSequencedAdvancement> {

    abstract val runner: AdvancementSequenceRunner

    open fun applicableTo(player: HumanAmongUsPlayer): Boolean = true

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
        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false,
    ) : AssignedAdvancement<SequencedAdvancement, AssignedSequencedAdvancement> {
        override fun toJson() = JsonNull
    }
}