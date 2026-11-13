package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.sequenced.FastVentKillAdvancement
import kotlinx.serialization.json.JsonElement
import net.kyori.adventure.text.Component
import java.util.*

interface Advancement<A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> {
    val id: String
    val name: Component
        get() = Component.translatable("advancement.$id")
    val description: Component
        get() = Component.translatable("advancement.$id.description")
    val descriptionOther: Component
        get() = Component.translatable("advancement.$id.description.other")

    /**
     * Should return true if displaying this advancement would give the player an advantage in the game.
     *
     * If true, we award the player with this advancement **after** the game finishes.
     * If false, we award the player with this advancement the moment they complete it.
     */
    val hasGameAdvantage: Boolean
        get() = false

    fun new(player: UUID): AA

    fun createOf(player: UUID, data: JsonElement): AA

    companion object {
        val advancements = listOf<Advancement<*, *>>(
            FastVentKillAdvancement,
        )

        val advancementMap = advancements.associateBy { it.id }
    }
}