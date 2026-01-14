package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.CooldownAbilityItem
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("UnstableApiUsage")
object VentAbility : Ability<VentAbility, VentAbility.AssignedVentAbility> {
    override val id: String = "vent"

    override fun assignTo(player: AmongUsPlayer) = AssignedVentAbility(player)

    class AssignedVentAbility(override val player: AmongUsPlayer) : AssignedAbility<VentAbility, AssignedVentAbility> {
        override val definition = VentAbility
        override val items: List<AbilityItem> = listOf(VentAbilityItem(this))
    }

    class VentAbilityItem(ability: AssignedAbility<*, *>) : CooldownAbilityItem(
        ability,
        "vent",
        Cooldown(ability.player.game.settings[SettingsKey.VENT_COOLDOWN].seconds, true)
    ) {
        override fun cooldownItem(): ItemStack {
            val itemStack = if (canUse()) activatedItem() else deactivatedItem()
            itemStack.setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.general.cooldown") {
                        args {
                            string("cooldown", cooldown.remaining().toString(DurationUnit.SECONDS, 0))
                            string("ability", ability.definition.id)
                        }
                    }
                }
            )
            return itemStack
        }

        override fun activatedItem(): ItemStack = ItemStack(Material.TRIPWIRE_HOOK).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable(
                        if (ability.player.isVented()) "ability.vent.vent.out"
                        else "ability.vent.vent.in"
                    )
                }
            )
        }

        override fun deactivatedItem() = ItemStack(Material.BARRIER).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.vent.vent.deactivate")
                }
            )
        }

        override fun canUse() = ability.player.game.ventManager.run {
            isVented(ability.player) || isNearVent(ability.player)
        }

        override fun onRightClick() {
            if (!cooldown.isFinished()) return
            if (!canUse()) return
            ability.player.game.ventManager.doVent(ability.player)
            cooldown.reset(start = true)
        }

        override fun onLeftClick() {
            if (!ability.player.isVented()) return
            ability.player.game.ventManager.nextVent(ability.player)
        }
    }
}