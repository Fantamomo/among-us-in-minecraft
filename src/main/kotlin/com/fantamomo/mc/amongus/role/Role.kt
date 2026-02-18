package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.*
import com.fantamomo.mc.amongus.role.imposters.ImposterRole
import com.fantamomo.mc.amongus.role.imposters.MinerRole
import com.fantamomo.mc.amongus.role.imposters.MorphlingRole
import com.fantamomo.mc.amongus.role.imposters.PhantomRole

interface Role<R : Role<R, A>, A : AssignedRole<R, A>> {
    val id: String
    val team: Team
    val canDoTask: Boolean
        get() = team.canDoTask

    /** A translation Key */
    val name: String
        get() = "role.$id.name"
    val description: String
        get() = "role.$id.description"

    val defaultAbilities: Set<Ability<*, *>>

    fun assignTo(player: AmongUsPlayer): A

    companion object {
        val roles: Set<Role<*, *>> = setOf(
            CrewmateRole,
            CameraManRole,
            EngineerRole,
            CallerRole,
            DetectiveRole,
            TheDamnedRole,
            MayorRole,
            SnitchRole,
            SheriffRole,

            ImposterRole,
            MinerRole,
            MorphlingRole,
            PhantomRole
        )
    }
}