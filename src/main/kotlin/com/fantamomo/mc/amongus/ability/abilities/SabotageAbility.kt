package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.Sabotage
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

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
                    ItemStack(sabotage.sabotageType.activeMaterial).apply {
                        setData(
                            DataComponentTypes.ITEM_NAME,
                            Component.translatable(
                                "ability.sabotage.${sabotage.sabotageType.id}.active"
                            )
                        )
                    }
                }

                onRightClick {
                    game.sabotageManager.sabotage(sabotage)

                    // Start cooldown after activation
                    getTimer("cooldown")?.start()
                }
            }

            // ---------- BLOCKED ----------

            state(AbilityItemState.BLOCKED) {

                render {

                    val reason = getBlockReason()

                    val key = when (reason) {

                        BlockReason.Sabotage ->
                            "ability.sabotage.disabled"

                        BlockReason.InMeeting ->
                            "ability.general.disabled.in_meeting"

                        BlockReason.InVent ->
                            "ability.general.disabled.in_vent"

                        else ->
                            "ability.sabotage.disabled"
                    }

                    ItemStack(sabotage.sabotageType.deactivateMaterial).apply {
                        setData(
                            DataComponentTypes.ITEM_NAME,
                            Component.translatable(key)
                        )
                    }
                }
            }

            // ---------- COOLDOWN ----------

            state(AbilityItemState.COOLDOWN) {

                render {
                    ItemStack(Material.BARRIER).apply {
                        setData(
                            DataComponentTypes.ITEM_NAME,
                            Component.translatable("ability.general.disabled.cooldown")
                        )
                    }
                }
            }
        }
    }
}