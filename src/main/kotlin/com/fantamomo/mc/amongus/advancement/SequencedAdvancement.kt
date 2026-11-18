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
        val jsonObject = data as? JsonObject
            ?: return AssignedSequencedAdvancement(this, player)
        val isCompleted = (jsonObject["is_completed"] as? JsonPrimitive)
            ?.booleanOrNull ?: false
        val startedParts = (jsonObject["started_parts"] as? JsonArray)
            ?.mapNotNullTo(mutableSetOf()) { (it as? JsonPrimitive)?.contentOrNull }
            ?: mutableSetOf()
        val completedParts = (jsonObject["completed_parts"] as? JsonArray)
            ?.mapNotNullTo(mutableSetOf()) { (it as? JsonPrimitive)?.contentOrNull }
            ?: mutableSetOf()
        return AssignedSequencedAdvancement(this, player, isCompleted, startedParts, completedParts)
    }

    class AssignedSequencedAdvancement(
        override val definition: SequencedAdvancement,
        override val player: UUID,
        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false,
        internal val startedParts: MutableSet<String> = mutableSetOf(),
        internal val completedParts: MutableSet<String> = mutableSetOf()
    ) : AssignedAdvancement<SequencedAdvancement, AssignedSequencedAdvancement> {
        override fun toJson(): JsonElement {
            if (startedParts.isEmpty() && completedParts.isEmpty()) return JsonNull
            return buildJsonObject {
                if (startedParts.isNotEmpty()) putJsonArray("started_parts") {
                    startedParts.forEach { add(it) }
                }
                if (completedParts.isNotEmpty()) putJsonArray("completed_parts") {
                    completedParts.forEach { add(it) }
                }
            }
        }

    }
}