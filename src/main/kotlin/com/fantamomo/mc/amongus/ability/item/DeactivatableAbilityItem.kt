package com.fantamomo.mc.amongus.ability.item

import com.fantamomo.mc.amongus.ability.AssignedAbility
import org.bukkit.inventory.ItemStack

abstract class DeactivatableAbilityItem(ability: AssignedAbility<*, *>, id: String) : AbilityItem(ability, id) {
    abstract fun activatedItem(): ItemStack
    abstract fun deactivatedItem(): ItemStack

    abstract fun canUse(): Boolean

    override fun getItemStack(): ItemStack {
        if (canUse()) return activatedItem()
        return deactivatedItem()
    }
}