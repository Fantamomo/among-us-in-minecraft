package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.CallMeetingAbility
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component

object CallerRole : Role<CallerRole, CallerRole.AssignedCallerRole> {
    override val id: String = "caller"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        CallMeetingAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedCallerRole(player)

    class AssignedCallerRole(override val player: AmongUsPlayer) : AssignedRole<CallerRole, AssignedCallerRole> {
        override val definition = CallerRole

        override fun scoreboardLine(): Component {
            val pressed = player.meetingButtonsPressed
            val max = player.game.settings[SettingsKey.MEETING.MEETING_BUTTONS]
            val value = max - pressed
            return textComponent {
                if (value <= 0) translatable("role.caller.scoreboard.limit_reached")
                else translatable("role.caller.scoreboard.left") {
                    args { numeric("count", value) }
                }
            }
        }
    }
}