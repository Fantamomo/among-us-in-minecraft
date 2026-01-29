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
import kotlin.time.DurationUnit

object KillAbility :
    Ability<KillAbility, KillAbility.AssignedKillAbility> {

    override val id = "kill"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedKillAbility(player)

    class AssignedKillAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<KillAbility, AssignedKillAbility> {

        override val definition = KillAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("kill") {

                // ---------- TIMER SETUP ----------

                val killCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.KILL_COOLDOWN]
                )

                // ---------- BLOCK CONDITIONS ----------

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
                    if (!game.killManager.canKillAsImposter(player))
                        BlockReason.custom("notNearVictim")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        ItemStack(Material.NETHER_STAR).apply {
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(
                                    "ability.kill.kill.active"
                                )
                            )
                        }
                    }

                    onRightClick {
                        game.killManager.killNearestAsImposter(player)

                        killCooldown.start()
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {

                        val reason = getBlockReason()

                        val key = when (reason) {

                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom ->
                                "ability.kill.kill.deactivate"

                            else ->
                                "ability.kill.kill.deactivate"
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

                        val seconds =
                            killCooldown.remaining()
                                .toInt(DurationUnit.SECONDS)

                        ItemStack(Material.BARRIER).apply {
                            amount = seconds.coerceAtLeast(1)
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(
                                    "ability.kill.kill.cooldown"
                                )
                            )
                        }
                    }
                }
            }
        )
    }
}
