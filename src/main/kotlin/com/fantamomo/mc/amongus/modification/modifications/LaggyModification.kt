package com.fantamomo.mc.amongus.modification.modifications

import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer

object LaggyModification : Modification<LaggyModification, LaggyModification.AssignedLaggyModification> {
    override val id: String = "laggy"

    override fun assignTo(player: AmongUsPlayer) = AssignedLaggyModification(player)

    class AssignedLaggyModification(override val player: AmongUsPlayer) :
        AssignedModification<LaggyModification, AssignedLaggyModification> {
        override val definition = LaggyModification

        private var ticks = 0

        fun shouldSync() = ticks % 20 == 0

        override fun onTick() {
            ticks++
        }
    }
}