package com.fantamomo.mc.amongus.modification.modifications

import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.Team

object TorchModification : Modification<TorchModification, TorchModification.AssignedTorchModification> {
    override val id: String = "torch"

    override fun canAssignTo(player: AmongUsPlayer) = player.assignedRole?.definition?.team != Team.IMPOSTERS

    override fun assignTo(player: AmongUsPlayer) = AssignedTorchModification(player)

    class AssignedTorchModification(override val player: AmongUsPlayer) : AssignedModification<TorchModification, AssignedTorchModification> {
        override val definition = TorchModification
    }
}