package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.goals.ReportBodyGoal
import com.fantamomo.mc.amongus.role.neutral.CannibalRole
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType

object ReportAbility :
    Ability<ReportAbility, ReportAbility.AssignedReportAbility> {

    override val id = "report"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedReportAbility(player)

    class AssignedReportAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<ReportAbility, AssignedReportAbility> {

        override val definition = ReportAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("report") {
                clickDelay = true

                // ---------- CONDITIONS ----------

                requiresNotInMeeting()

                requiresNotInVent()

                condition(
                    BlockReason.custom("notNearCorpse"),
                    Component.translatable("ability.report.report.tooltip")
                ) {
                    val loc = player.location
                    !game.killManager.isNearCorpse(loc)
                }

                requiresAlive()

                registerGoals { selector, zombie, item ->
                    if (zombie.controller.player.role.definition !== CannibalRole && !zombie.controller.player.hasAbility(EatBodyAbility)) {
                        selector.addGoal(1, ReportBodyGoal(zombie))
                    }
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.FIREWORK_ROCKET
                        translationKey = "ability.report.report.active"
                    }

                    onRightClick {
                        game.meetingManager.callMeeting(
                            player,
                            MeetingManager.MeetingReason.BODY
                        )
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        when (val reason = ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                translationKey = "ability.report.report.deactivate.dead"

                            is BlockReason.Custom -> translationKey = when (reason.id) {
                                "notNearCorpse" ->
                                    "ability.report.report.deactivate"

                                else ->
                                    "ability.report.report.deactivate"
                            }

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}
