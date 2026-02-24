package com.fantamomo.mc.amongus.modification

import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.modification.modifications.LaggyModification
import com.fantamomo.mc.amongus.modification.modifications.RadarModification
import com.fantamomo.mc.amongus.modification.modifications.SmallModification
import com.fantamomo.mc.amongus.modification.modifications.TorchModification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component

interface Modification<M : Modification<M, A>, A : AssignedModification<M, A>> {
    val id: String

    val name: Component
        get() = Component.translatable("modification.$id.name")
    val description: Component
        get() = Component.translatable("modification.$id.description")

    fun canAssignTo(player: AmongUsPlayer): Boolean = true

    fun assignTo(player: AmongUsPlayer): A

    companion object {
        val modifications: List<Modification<*, *>> = listOf(
            TorchModification,
            LaggyModification,
            SmallModification,
            RadarModification
        ).filter { it.id !in AmongUsConfig.Modifications.disabled }

        private val sharedShuffledModification = modifications.toMutableList()

        private fun sharedShuffledModification(): List<Modification<*, *>> = sharedShuffledModification.apply { shuffle() }

        fun randomModification(player: AmongUsPlayer): AssignedModification<*, *>? =
            sharedShuffledModification().find { it.canAssignTo(player) }?.assignTo(player)
    }
}