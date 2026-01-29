package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.time.Duration.Companion.seconds

object VentAbility :
    Ability<VentAbility, VentAbility.AssignedVentAbility> {

    override val id = "vent"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedVentAbility(player)

    class AssignedVentAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<VentAbility, AssignedVentAbility> {

        override val definition = VentAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("vent") {

                // ---------- MODULAR TIMER ----------

                timer("cooldown", player.game.settings[SettingsKey.VENT_COOLDOWN].seconds)

                // ---------- CONDITIONS ----------

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    val ventManager = game.ventManager

                    if (!ventManager.isVented(player) &&
                        !ventManager.isNearVent(player)
                    )
                        BlockReason.custom("notNearVent")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {

                        val key =
                            if (player.isVented())
                                "ability.vent.vent.out"
                            else
                                "ability.vent.vent.in"

                        ItemStack(Material.TRIPWIRE_HOOK).apply {
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(key)
                            )
                        }
                    }

                    onRightClick {

                        game.ventManager.doVent(player)

                        // Start cooldown when toggling vent
                        getTimer("cooldown")?.start()
                    }

                    onLeftClick {

                        if (!player.isVented()) return@onLeftClick

                        game.ventManager.nextVent(player)
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {

                        val reason = conditions
                            .firstNotNullOfOrNull { it() }

                        val key = when (reason) {

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom ->
                                "ability.vent.vent.deactivate"

                            else ->
                                "ability.vent.vent.deactivate"
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
        )
    }
}