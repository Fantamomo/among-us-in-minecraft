package com.fantamomo.mc.amongus.ability

import com.fantamomo.mc.amongus.ability.abilities.*
import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface Ability<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val id: String

    fun canAssignTo(player: AmongUsPlayer): Boolean = true

    fun assignTo(player: AmongUsPlayer): S

    companion object {
        val abilities = listOf(
            ArsonistAbility,
            CallMeetingAbility,
            CallMeetingAbility,
            CreateVentAbility,
            EatBodyAbility,
            GhostFormAbility,
            KillAbility,
            MorphAbility,
            RemoteCameraAbility,
            ReportAbility,
            RevealTeamAbility,
            SabotageAbility,
            SheriffKillAbility,
            VentAbility
        )
    }
}