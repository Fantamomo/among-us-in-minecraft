package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.*
import com.fantamomo.mc.amongus.role.imposters.*
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

        private fun createSet(vararg roles: Role<*, *>) =
            roles.filter { it.id !in AmongUsConfig.Roles.disabled }.toSet()

        val crewmates: Set<Role<*, *>> = createSet(
            CrewmateRole,
            CameraManRole,
            EngineerRole,
            CallerRole,
            DetectiveRole,
            TheDamnedRole,
            MayorRole,
            SnitchRole,
            SheriffRole,
            SeerRole
        )
        val imposters: Set<Role<*, *>> = createSet(
            ImposterRole,
            MinerRole,
            MorphlingRole,
            PhantomRole,
            CamouflagerRole
        )
        val neutrals: Set<Role<*, *>> = createSet(
            JesterRole,
            CannibalRole,
            ArsonistRole
        )

        val roles: Set<Role<*, *>> = crewmates + imposters + neutrals
    }
}