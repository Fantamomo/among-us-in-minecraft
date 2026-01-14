package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.DeactivatableAbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import org.bukkit.inventory.ItemStack

object ReportAbility : Ability<ReportAbility, ReportAbility.AssignedReportAbility> {
    override val id: String = "report"

    override fun assignTo(player: AmongUsPlayer) = AssignedReportAbility(player)

    class AssignedReportAbility(override val player: AmongUsPlayer) : AssignedAbility<ReportAbility, AssignedReportAbility> {
        override val definition = ReportAbility
        override val items: List<AbilityItem> = listOf(ReportAbility.ReportAbilityItem(this))
    }

    class ReportAbilityItem(ability: AssignedAbility<*, *>) : DeactivatableAbilityItem(ability, "report") {
        override fun activatedItem(): ItemStack {
            TODO("Not yet implemented")
        }

        override fun deactivatedItem(): ItemStack {
            TODO("Not yet implemented")
        }

        override fun canUse(): Boolean {
            TODO("Not yet implemented")
        }

        override fun onRightClick() {
            TODO("Not yet implemented")
        }
    }
}