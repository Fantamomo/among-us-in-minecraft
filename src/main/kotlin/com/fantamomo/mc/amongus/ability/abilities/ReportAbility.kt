package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

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

                // ---------- CONDITIONS ----------

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    if (player.isVented())
                        BlockReason.InVent
                    else null
                }

                condition {
                    val loc = player.livingEntity.location
                    if (!game.killManager.isNearCorpse(loc))
                        BlockReason.custom("notNearCorpse")
                    else null
                }

                condition {
                    if (!player.isAlive)
                        BlockReason.custom("notAlive")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        ItemStack(Material.FIREWORK_ROCKET).apply {
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(
                                    "ability.report.report.active"
                                )
                            )
                        }
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

                        val reason = getBlockReason()

                        val key = when (reason) {

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            is BlockReason.Custom -> when (reason.id) {
                                "notNearCorpse" ->
                                    "ability.report.report.deactivate"

                                "notAlive" ->
                                    "ability.report.report.deactivate.dead"

                                else ->
                                    "ability.report.report.deactivate"
                            }

                            else ->
                                "ability.report.report.deactivate"
                        }

                        ItemStack(Material.BARRIER).apply {
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(key)
                            )
                        }
                    }
                }

                // ---------- COOLDOWN ----------

                state(AbilityItemState.COOLDOWN) {
                    render { ItemStack(Material.BARRIER) }
                }
            }
        )
    }
}
