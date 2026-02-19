package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.builder.itemType
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.Sabotage
import org.bukkit.inventory.ItemType

object SabotageAbility :
    Ability<SabotageAbility, SabotageAbility.AssignedSabotageAbility> {

    override val id = "sabotage"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedSabotageAbility(player)

    class AssignedSabotageAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<SabotageAbility, AssignedSabotageAbility> {

        override val definition = SabotageAbility

        override val items: List<AbilityItem> =
            player.game.sabotageManager.supportedSabotages.values
                .map(::createItem)

        @Suppress("UnstableApiUsage")
        private fun createItem(
            sabotage: Sabotage<*, *>
        ): AbilityItem = abilityItem(sabotage.sabotageType.id) {

            // ---------- COOLDOWN (MODULAR TIMER) ----------

            setTimer("cooldown", player.game.sabotageManager.cooldown(sabotage))

            // ---------- CONDITIONS ----------

            condition {
                if (game.sabotageManager.isCurrentlySabotage())
                    BlockReason.Sabotage
                else null
            }

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

            // ---------- ACTIVE ----------

            state(AbilityItemState.ACTIVE) {

                render {
                    itemType(sabotage.sabotageType.activeItemType)
                    translationKey = "ability.sabotage.${sabotage.sabotageType.id}.active"

                }

                onRightClick {
                    game.sabotageManager.sabotage(sabotage)
                }
            }

            // ---------- BLOCKED ----------

            state(AbilityItemState.BLOCKED) {
                render {
                    itemType(sabotage.sabotageType.deactivateMaterial)
                    translationKey = when (ctx.getBlockReason()) {

                        BlockReason.Sabotage ->
                            "ability.sabotage.disabled"

                        BlockReason.InMeeting ->
                            "ability.general.disabled.in_meeting"

                        BlockReason.InVent ->
                            "ability.general.disabled.in_vent"

                        else ->
                            "ability.sabotage.disabled"
                    }
                }
            }

            // ---------- COOLDOWN ----------

            state(AbilityItemState.COOLDOWN) {
                render {
                    itemType = ItemType.BARRIER
                    translationKey = "ability.general.disabled.cooldown"
                }
            }
        }
    }
}