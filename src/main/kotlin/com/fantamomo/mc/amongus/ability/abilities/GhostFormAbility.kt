package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.translateTo
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType

object GhostFormAbility : Ability<GhostFormAbility, GhostFormAbility.AssignedGhostFormAbility> {
    override val id: String = "ghost_form"

    override fun assignTo(player: AmongUsPlayer) = AssignedGhostFormAbility(player)

    class AssignedGhostFormAbility(override val player: AmongUsPlayer) : AssignedAbility<GhostFormAbility, AssignedGhostFormAbility> {
        override val definition = GhostFormAbility
        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("ghost_form") {
                val ghostCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.GHOST_FORM_COOLDOWN]
                )

                // ---------- BLOCK CONDITIONS ----------

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
                    if (player.isVented())
                        BlockReason.InVent
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    @Suppress("UnstableApiUsage")
                    renderOverride {
                        val ghostPlayer = player.game.ghostFormManager.getGhostPlayer(player)
                        val item = ItemStack(Material.WIND_CHARGE)
                        if (ghostPlayer == null) {
                            item.setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable("ability.ghost_form.ghost_form.active").translateTo(player.locale)
                            )
                        } else {
                            item.setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable("ability.ghost_form.ghost_form.active.exit")
                                    .translateTo(player.locale)
                            )
                            item.amount = ghostPlayer.remainingTime.inWholeSeconds.toInt().coerceIn(1, 99)
                        }
                        return@renderOverride item
                    }

                    onRightClick {
                        restartCooldown = false
                        val ghostPlayer = player.game.ghostFormManager.getGhostPlayer(player)
                        if (ghostPlayer == null) {
                            player.game.ghostFormManager.joinGhostForm(player, ghostCooldown)
                        } else {
                            player.game.ghostFormManager.exit(player)
//                            ghostCooldown.start(game.settings[SettingsKey.ROLES.GHOST_FORM_COOLDOWN])
                        }
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        when (ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                translationKey = "ability.kill.kill.dead"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}