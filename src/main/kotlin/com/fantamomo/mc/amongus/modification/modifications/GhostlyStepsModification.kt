package com.fantamomo.mc.amongus.modification.modifications

import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.DetectiveRole

object GhostlyStepsModification : Modification<GhostlyStepsModification, GhostlyStepsModification.AssignedGhostlyStepsModification> {
    override val id: String = "ghostly_steps"

    override fun canAssignTo(player: AmongUsPlayer): Boolean {
        if (player.role.definition === DetectiveRole) return false
        return player.game.players.any { it.role.definition === DetectiveRole }
    }

    override fun assignTo(player: AmongUsPlayer) = AssignedGhostlyStepsModification(player)

    class AssignedGhostlyStepsModification(override val player: AmongUsPlayer) : AssignedModification<GhostlyStepsModification, AssignedGhostlyStepsModification> {
        override val definition = GhostlyStepsModification
    }
}