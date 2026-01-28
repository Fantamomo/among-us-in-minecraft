package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import org.bukkit.Material

object ReportAbility : Ability<ReportAbility, ReportAbility.AssignedReportAbility> {
    override val id: String = "report"

    override fun assignTo(player: AmongUsPlayer) = AssignedReportAbility(player)

    class AssignedReportAbility(override val player: AmongUsPlayer) : AssignedAbility<ReportAbility, AssignedReportAbility> {
        override val definition = ReportAbility
        override val items: List<AbilityItem> = listOf(
            abilityItem("report") {
                material {
                    active = Material.FIREWORK_ROCKET
                    inactive = Material.BARRIER
                }

                name {
                    active("ability.report.report.active")
                    inactive {
                        whenBlocked(
                            BlockReason.InMeeting,
                            "ability.general.disabled.in_meeting"
                        )
                        whenBlocked(
                            BlockReason.InVent,
                            "ability.general.disabled.in_vent"
                        )
                        whenBlocked(
                            "notNearCorpse",
                            "ability.report.report.deactivate"
                        )
                        whenBlocked(
                            "notAlive",
                            "ability.report.report.deactivate.dead"
                        )
                    }
                }

                blockWhen {
                    inMeeting()
                    inVent()
                    custom("notNearCorpse") {
                        val location = player.livingEntity.location
                        !game.killManager.isNearCorpse(location)
                    }
                    custom("notAlive") {
                        player.isAlive
                    }
                }

                onRightClick {
                    game.meetingManager.callMeeting(player, MeetingManager.MeetingReason.BODY)
                }
            }
        )
    }
}