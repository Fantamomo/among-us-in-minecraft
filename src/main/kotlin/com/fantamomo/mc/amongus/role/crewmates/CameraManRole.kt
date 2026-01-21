package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.RemoteCameraAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object CameraManRole : Role<CameraManRole, CameraManRole.AssignedCameraManRole> {
    override val id: String = "cameraman"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        RemoteCameraAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedCameraManRole(player)

    class AssignedCameraManRole(override val player: AmongUsPlayer) : AssignedRole<CameraManRole, AssignedCameraManRole> {
        override val definition = CameraManRole
    }
}