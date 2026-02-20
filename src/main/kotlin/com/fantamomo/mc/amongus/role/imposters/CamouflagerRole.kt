package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.CamouflageAbility
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.marker.KillerRole
import com.fantamomo.mc.amongus.util.toSmartString
import net.kyori.adventure.text.Component
import kotlin.time.DurationUnit

object CamouflagerRole : Role<CamouflagerRole, CamouflagerRole.AssignedCamouflagerRole>, KillerRole {
    override val id: String = "camouflager"
    override val team: Team = Team.IMPOSTERS
    override val defaultAbilities: Set<Ability<*, *>> = ImposterRole.defaultAbilities + CamouflageAbility

    override fun assignTo(player: AmongUsPlayer) = AssignedCamouflagerRole(player)

    class AssignedCamouflagerRole(override val player: AmongUsPlayer) : AssignedRole<CamouflagerRole, AssignedCamouflagerRole> {
        override val definition = CamouflagerRole

        override fun scoreboardLine(): Component? {
            val morphManager = player.game.morphManager
            val target = morphManager.camouflageTarget() ?: return null
            val remaining = morphManager.remainingCamouflageTime() ?: return null
            return textComponent {
                translatable("role.camouflager.scoreboard") {
                    args {
                        string("player", target.name)
                        string("time", remaining.toSmartString(DurationUnit.SECONDS))
                    }
                }
            }
        }
    }
}