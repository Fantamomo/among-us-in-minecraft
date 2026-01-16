package com.fantamomo.mc.amongus.ability

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.DeactivatableAbilityItem
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object AbilityManager {
    private val abilities: MutableSet<AssignedAbility<*, *>> = mutableSetOf()
    private var taskId: Int = -1

    fun registerAbility(ability: AssignedAbility<*, *>) {
        abilities.add(ability)
        if (taskId == -1) {
            taskId = AmongUs.server.scheduler.scheduleSyncRepeatingTask(AmongUs, {
                update()
            }, 0L, 1L)
        }
    }

    private fun update() {
        abilities.forEach { ability ->
            val items = ability.items
            for (item in items) {
                if (item is DeactivatableAbilityItem) {
                    ability.player.notifyAbilityItemChange(item)
                }
            }
        }
    }

    fun isAbilityItem(item: ItemStack): Boolean {
        val pdc = item.persistentDataContainer
        return pdc.has(AbilityItem.ABILITY_UUID)
    }

    fun itemRightClick(item: ItemStack, player: Player): Boolean {
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return false
        val pdc = item.persistentDataContainer
        val abilityId = pdc.get(AbilityItem.ABILITY_ID, PersistentDataType.STRING) ?: return false
        val uuid = pdc.get(AbilityItem.ABILITY_UUID, CustomPersistentDataTypes.UUID) ?: return false
        for (ability in abilities) {
            if (ability.player != amongUsPlayer) continue
            if (ability.definition.id != abilityId) continue
            for (abilityItem in ability.items) {
                if (abilityItem.uuid != uuid) continue
                abilityItem.onRightClick()
                abilityItem.ability.player.notifyAbilityItemChange(abilityItem)
                return true
            }
        }
        return false
    }

    fun itemLeftClick(item: ItemStack, player: Player): Boolean {
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return false
        val pdc = item.persistentDataContainer
        val abilityId = pdc.get(AbilityItem.ABILITY_ID, PersistentDataType.STRING) ?: return false
        val uuid = pdc.get(AbilityItem.ABILITY_UUID, CustomPersistentDataTypes.UUID) ?: return false
        for (ability in abilities) {
            if (ability.player != amongUsPlayer) continue
            if (ability.definition.id != abilityId) continue
            for (abilityItem in ability.items) {
                if (abilityItem.uuid != uuid) continue
                abilityItem.onLeftClick()
                abilityItem.ability.player.notifyAbilityItemChange(abilityItem)
                return true
            }
        }
        return false
    }
}