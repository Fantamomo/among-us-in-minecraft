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
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

object MorphAbility : Ability<MorphAbility, MorphAbility.AssignedMorphAbility> {
    override val id: String = "morph"

    override fun assignTo(player: AmongUsPlayer) = AssignedMorphAbility(player)

    class AssignedMorphAbility(override val player: AmongUsPlayer) :
        AssignedAbility<MorphAbility, AssignedMorphAbility> {
        override val definition = MorphAbility
        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("morph") {
                val morphCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.MORPHLING_MORPH_COOLDOWN]
                )

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

                state(AbilityItemState.ACTIVE) {
                    @Suppress("UnstableApiUsage")
                    renderOverride {
                        val morphedPlayer = player.game.morphManager.getMorphedPlayer(player)
                        if (morphedPlayer == null) {
                            val item = ItemStack(Material.PLAYER_HEAD)
                            item.setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable("ability.morph.morph.active.morph").translateTo(player.locale)
                            )
                            return@renderOverride item
                        }
                        val item = ItemType.PLAYER_HEAD.createItemStack {
                            it.playerProfile = morphedPlayer.target.profile
                        }
                        item.setData(
                            DataComponentTypes.CUSTOM_NAME, // CUSTOM_NAME is used instead of ITEM_NAME since ITEM_NAME is ineffective on a PLAYER_HEAD that has a texture.
                            Component.translatable("ability.morph.morph.active.unmorph").translateTo(player.locale)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                        item.amount = morphedPlayer.remainingTime.inWholeSeconds.toInt().coerceIn(1, 99)
                        return@renderOverride item
                    }

                    onRightClick {
                        restartCooldown = false

                        morphCooldown.pause()

                        if (player.game.morphManager.isMorphed(player)) {
                            player.game.morphManager.unmorph(player)
                            val duration = player.game.settings[SettingsKey.ROLES.MORPHLING_MORPH_COOLDOWN]
                            morphCooldown.start(duration)
                            return@onRightClick
                        }

                        game.morphManager.showMorphInventory(player) { success ->
                            morphCooldown.start(5.seconds)
                        }
                    }
                }

                state(AbilityItemState.BLOCKED) {
                    render {
                        itemType = ItemType.BARRIER
                        translationKey = when (ctx.getBlockReason()) {
                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            else ->
                                "ability.morph.morph.deactivate"
                        }
                    }
                }

                state(AbilityItemState.COOLDOWN) {
                    render {
                        itemType = ItemType.BARRIER
                        translationKey = "ability.general.disabled.cooldown"
                    }
                }
            }
        )
    }
}