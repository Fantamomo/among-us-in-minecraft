package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.MorphAbility
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object MorphlingRole : Role<MorphlingRole, MorphlingRole.AssignedMorphlingRole> {
    override val id: String = "morphling"
    override val team: Team = Team.IMPOSTERS
    override val defaultAbilities: Set<Ability<*, *>> = ImposterRole.defaultAbilities + MorphAbility

    override fun assignTo(player: AmongUsPlayer) = AssignedMorphlingRole(player)

    class AssignedMorphlingRole(override val player: AmongUsPlayer) : AssignedRole<MorphlingRole, AssignedMorphlingRole> {
        override val definition = MorphlingRole

        override fun scoreboardLine() = player.game.morphManager.getMorphedPlayer(player)?.let { morphedPlayer ->
            textComponent {
                translatable("role.morphling.scoreboard") {
                    args {
                        string("target", morphedPlayer.target.name)
                    }
                }
            }
        }
    }
}