package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.CooldownAbilityItem
import com.fantamomo.mc.amongus.ability.item.game
import com.fantamomo.mc.amongus.manager.SabotageManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.inventory.ItemStack

object SabotageAbility : Ability<SabotageAbility, SabotageAbility.AssignedSabotageAbility> {
    override val id: String = "sabotage"

    override fun assignTo(player: AmongUsPlayer) = AssignedSabotageAbility(player)

    class AssignedSabotageAbility(override val player: AmongUsPlayer) : AssignedAbility<SabotageAbility, AssignedSabotageAbility> {
        override val definition = SabotageAbility
        override val items: List<AbilityItem> = player.game.sabotageManager.supportedSabotages.map { SabotageAbilityItem(this, it) }
    }

    @Suppress("UnstableApiUsage")
    class SabotageAbilityItem(ability: AssignedAbility<*, *>, val sabotageType: SabotageManager.SabotageType) : CooldownAbilityItem(
        ability,
        sabotageType.id,
        ability.player.game.sabotageManager.getCooldown(sabotageType)
    ) {
        override fun activatedItem() = ItemStack(sabotageType.activeItem).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable(
                        "ability.sabotage.${sabotageType.id}.active"
                    )
                }
            )
        }

        override fun deactivatedItem() = ItemStack(sabotageType.deactivatedItem).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable(
                        "ability.sabotage.${sabotageType.id}.deactivate"
                    )
                }
            )
        }

        override fun canUse(): Boolean = game.sabotageManager.canSabotage(sabotageType)

        override fun onRightClick() {
            if (!canUse()) return
            game.sabotageManager.sabotage(sabotageType)
            notifyItemChange()
        }
    }
}