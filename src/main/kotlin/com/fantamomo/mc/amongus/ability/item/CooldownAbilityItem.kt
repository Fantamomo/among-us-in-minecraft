package com.fantamomo.mc.amongus.ability.item

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.UseCooldown
import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt
import kotlin.time.DurationUnit

abstract class CooldownAbilityItem(ability: AssignedAbility<*, *>, id: String, val cooldown: Cooldown) :
    DeactivatableAbilityItem(ability, id) {

    init {
        cooldown.onFinish(::notifyItemChange)
        cooldown.tickCondition(::shouldCoundDown)
    }

    override fun canUse() = true

    override fun getItemStack() = when {
        !cooldown.isFinished() -> getCooldownItem()
        canUse() -> activatedItem()
        else -> deactivatedItem()
    }

    override fun startCooldown() {
        cooldown.start()
    }

    protected open fun getCooldownItem(): ItemStack {
        val item = cooldownItem()
        val key = Key.key("amongus:ability_cooldown/${ability.definition.id}/$id")
        val seconds = cooldown.remaining().toInt(DurationUnit.MILLISECONDS) / 1000.0

        @Suppress("UnstableApiUsage")
        item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(Float.MAX_VALUE / 2).cooldownGroup(key))
        ability.player.player?.setCooldown(key, Int.MAX_VALUE / 2)
        item.amount = seconds.roundToInt().coerceAtLeast(1).coerceAtMost(99)
        return item
    }

    @Suppress("UnstableApiUsage")
    protected open fun cooldownItem(): ItemStack {
        val itemStack = if (canUse()) activatedItem() else deactivatedItem()
        itemStack.setData(
            DataComponentTypes.ITEM_NAME,
            textComponent(ability.player.locale) {
                translatable("ability.general.disabled.cooldown") {
                    args {
                        string("cooldown", cooldown.remaining().toString(DurationUnit.SECONDS, 0))
                        string("ability", ability.definition.id)
                    }
                }
            }
        )
        return itemStack
    }

    protected open fun shouldCoundDown() = true
}