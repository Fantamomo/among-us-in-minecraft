package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.GhostFormAbility
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.marker.KillerRole
import com.fantamomo.mc.amongus.util.toSmartString
import kotlin.time.DurationUnit

object PhantomRole : Role<PhantomRole, PhantomRole.AssignedPhantomRole>, KillerRole {
    override val id: String = "phantom"
    override val team: Team = Team.IMPOSTERS
    override val defaultAbilities: Set<Ability<*, *>> = ImposterRole.defaultAbilities + GhostFormAbility

    override fun assignTo(player: AmongUsPlayer) = AssignedPhantomRole(player)

    class AssignedPhantomRole(override val player: AmongUsPlayer) : AssignedRole<PhantomRole, AssignedPhantomRole> {
        override val definition = PhantomRole

        override fun scoreboardLine() = player.game.ghostFormManager.getGhostPlayer(player)?.let { ghostPlayer ->
            textComponent {
                translatable("role.phantom.scoreboard") {
                    args {
                        string("time", ghostPlayer.remainingTime.toSmartString(DurationUnit.SECONDS))
                    }
                }
            }
        }
    }
}