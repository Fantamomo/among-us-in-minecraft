package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.*
import com.fantamomo.mc.amongus.role.imposters.ImposterRole
import com.fantamomo.mc.amongus.role.imposters.MinerRole
import com.fantamomo.mc.amongus.role.imposters.MorphlingRole
import com.fantamomo.mc.amongus.role.imposters.PhantomRole
import com.fantamomo.mc.amongus.role.neutral.ArsonistRole
import com.fantamomo.mc.amongus.role.neutral.CannibalRole
import com.fantamomo.mc.amongus.role.neutral.JesterRole
import net.kyori.adventure.text.Component

interface Role<R : Role<R, A>, A : AssignedRole<R, A>> {
    val id: String
    val team: Team
    val canDoTask: Boolean
        get() = team.canDoTask

    val name: Component
        get() = Component.translatable("role.$id.name")
    val description: Component
        get() = Component.translatable("role.$id.description")

    val defaultAbilities: Set<Ability<*, *>>

    fun assignTo(player: AmongUsPlayer): A

    companion object {
        val crewmates: Set<Role<*, *>> = setOf(
            CrewmateRole,
            CameraManRole,
            EngineerRole,
            CallerRole,
            DetectiveRole,
            TheDamnedRole,
            MayorRole,
            SnitchRole,
            SheriffRole
        )
        val imposters: Set<Role<*, *>> = setOf(
            ImposterRole,
            MinerRole,
            MorphlingRole,
            PhantomRole
        )
        val neutrals: Set<Role<*, *>> = setOf(
            JesterRole,
            CannibalRole,
            ArsonistRole
        )

        val roles: Set<Role<*, *>> = crewmates + imposters + neutrals
    }
}