package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
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
                    player.game.settings[SettingsKey.ROLES.ARSONIST.DOUSE_COOLDOWN]
                )

                requiresAlive()

                requiresNotInMeeting()

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
                        when (ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                translationKey = "ability.arsonist.douse.dead"

                            is BlockReason.Custom ->
                                translationKey = "ability.arsonist.douse.deactivate.not_near_undoused_player"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}