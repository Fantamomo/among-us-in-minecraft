package com.fantamomo.mc.amongus.ability

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.builder.DSLAbilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

object AbilityManager {

    private val abilities = mutableSetOf<AssignedAbility<*, *>>()
    private var taskId = -1
    private var ticks: Int = 0

    private fun init() {
        if (taskId != -1) return
        taskId = AmongUs.server.scheduler.scheduleSyncRepeatingTask(AmongUs, ::tick, 0L, 1L)
    }

    private fun tick() {
        ticks++
        if (ticks % 10 == 0) {
            invalidateAll()
        }
    }

    private fun invalidateAll() {
        for (ability in abilities) {
            invalidateAbility(ability)
        }
    }

    fun invalidateAll(game: Game) {
        for (player in game.players) {
            invalidatePlayer(player)
        }
    }

    fun registerAbility(ability: AssignedAbility<*, *>) {
        init()
        abilities += ability
    }

    fun invalidateAbility(ability: AssignedAbility<*, *>) {
        ability.items.asSequence().filterIsInstance<DSLAbilityItem>()
            .forEach { it.invalidate() }
    }

    fun invalidatePlayer(player: AmongUsPlayer) {
        for (ability in player.abilities) {
            invalidateAbility(ability)
        }
    }

    fun itemRightClick(item: ItemStack, player: Player): Boolean {
        return handle(item, player) { it.onRightClick() }
    }

    fun itemLeftClick(item: ItemStack, player: Player): Boolean {
        return handle(item, player) { it.onLeftClick() }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun handle(
        item: ItemStack,
        player: Player,
        action: (AbilityItem) -> Unit
    ): Boolean {
        contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return false
        val pdc = item.persistentDataContainer

        val abilityId = pdc.get(AbilityItem.ABILITY_ID, PersistentDataType.STRING) ?: return false
        val uuid = pdc.get(AbilityItem.ABILITY_UUID, CustomPersistentDataTypes.UUID) ?: return false

        val ability = abilities.firstOrNull {
            it.player == amongUsPlayer && it.definition.id == abilityId
        } ?: return false

        val abilityItem = ability.items.firstOrNull { it.uuid == uuid } ?: return false

        action(abilityItem)
        return true
    }

    fun isAbilityItem(stack: ItemStack) =
        stack.persistentDataContainer.has(AbilityItem.ABILITY_ID, PersistentDataType.STRING)
}