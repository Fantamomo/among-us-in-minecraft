package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.neutral.ArsonistRole
import com.fantamomo.mc.amongus.role.neutral.ArsonistRole.AssignedArsonistRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.inventory.ItemType

object ArsonistAbility : Ability<ArsonistAbility, ArsonistAbility.AssignedArsonistAbility> {
    override val id: String = "arsonist"

    override fun canAssignTo(player: AmongUsPlayer) = player.assignedRole?.definition === ArsonistRole

    override fun assignTo(player: AmongUsPlayer) = AssignedArsonistAbility(player)

    class AssignedArsonistAbility(override val player: AmongUsPlayer) : AssignedAbility<ArsonistAbility, AssignedArsonistAbility> {
        override val definition = ArsonistAbility

        private val arsonist: AssignedArsonistRole
            get() = player.assignedRole as? AssignedArsonistRole ?: error("Player does not have assigned Arsonist role")

        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("douse") {

                // ---------- TIMER SETUP ----------

                val douseCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.ARSONIST_DOUSE_COOLDOWN]
                )

                condition {
                    if (!player.isAlive) BlockReason.Dead
                    else null
                }

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    if (!arsonist.nearUndousedPlayer())
                        BlockReason.Custom("notNearUndousedPlayer")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.FLINT_AND_STEEL
                        translationKey = "ability.arsonist.douse.active"
                    }

                    onRightClick {
                        arsonist.douseNearest()
                        douseCooldown.start()
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        translationKey = when (ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                "ability.arsonist.douse.dead"
                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom ->
                                "ability.arsonist.douse.deactivate.not_near_undoused_player"

                            else ->
                                "ability.arsonist.douse.deactivate"
                        }
                    }
                }
            }
        )
    }
}