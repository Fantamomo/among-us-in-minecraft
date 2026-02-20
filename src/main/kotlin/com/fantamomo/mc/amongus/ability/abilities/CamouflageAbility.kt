package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.inventory.ItemType

object CamouflageAbility : Ability<CamouflageAbility, CamouflageAbility.AssignedCamouflageAbility> {
    override val id: String = "camouflage"

    override fun assignTo(player: AmongUsPlayer) = AssignedCamouflageAbility(player)

    class AssignedCamouflageAbility(override val player: AmongUsPlayer) : AssignedAbility<CamouflageAbility, AssignedCamouflageAbility> {
        override val definition = CamouflageAbility
        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("camouflage") {
                val camouflageCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.CAMOUFLAGE.DURATION]
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
                    render {
                        itemType = ItemType.MAGMA_CREAM
                        translationKey = "ability.camouflage.camouflage.active"
                    }

                    onRightClick {
                        restartCooldown = false
                        val morphManager = player.game.morphManager
                        if (morphManager.isCamouflageMode()) return@onRightClick
                        morphManager.camouflageMode()
                        camouflageCooldown.start(player.game.settings[SettingsKey.ROLES.CAMOUFLAGE.COOLDOWN] + player.game.settings[SettingsKey.ROLES.CAMOUFLAGE.DURATION])
                    }
                }
            }
        )
    }
}