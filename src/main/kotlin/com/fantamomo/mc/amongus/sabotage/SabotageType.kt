package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.util.isBetween
import org.bukkit.Material

sealed interface SabotageType<S : SabotageType<S, A>, A : AssignedSabotageType<S, A>> {
    val id: String
    val activeMaterial: Material
    val deactivateMaterial: Material
        get() = Material.BARRIER
    val isCrisis: Boolean
    val stopOnBodyReport: Boolean
    val canCallEmergencyMeeting: Boolean
        get() = false

    fun create(game: Game): AssignedSabotageType<S, A>?

    companion object {
        val types: Set<SabotageType<*, *>> = setOf(Lights)
    }

    object Lights : SabotageType<Lights, LightsSabotage> {
        override val id: String = "lights"
        override val activeMaterial = Material.REDSTONE_TORCH
        override val isCrisis: Boolean = false
        override val stopOnBodyReport: Boolean = false

        override fun create(game: Game): LightsSabotage? {
            val area = game.area
            val max = area.lightPosMax
            val min = area.lightPosMin
            if (min == null || max == null) return null
            if (area.lightLevers.none { it.isBetween(min, max) }) return null
            return LightsSabotage(game)
        }
    }
}