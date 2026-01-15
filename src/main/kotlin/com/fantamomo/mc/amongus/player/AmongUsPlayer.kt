package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import org.bukkit.DyeColor
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import java.util.*

class AmongUsPlayer internal constructor(
    val uuid: UUID,
    name: String,
    val game: Game,
    val locationBeforeGame: Location? = null
) {
    private var _name: String = name
    private var _locale: Locale = Locale.getDefault()
    private val abilities: MutableList<AssignedAbility<*, *>> = mutableListOf()

    val name: String
        get() = player?.name?.also { _name = it } ?: _name
    val livingEntity: LivingEntity
        get() = player ?: mannequin ?: throw IllegalStateException("AmongUsPlayer has no player or mannequin")
    var player: Player? = null
    var color: DyeColor = game.randomDyeColor()
    val locale: Locale
        get() = player?.locale()?.also { _locale = it } ?: _locale
    var assignedRole: AssignedRole<*, *>? = null

    var mannequin: Mannequin? = null

    private fun checkGameRunning() {
        if (game.phase != GamePhase.RUNNING) throw IllegalStateException("Cannot perform this action in this phase")
    }

    internal fun notifyAbilityItemChange(item: AbilityItem) {
        // checkGameRunning()
        val player = player ?: return
        val uuid = item.uuid
        val slots = player.inventory.mapIndexedNotNull { index, stack ->
            index.takeIf {
                stack?.persistentDataContainer?.get(
                    AbilityItem.ABILITY_UUID,
                    CustomPersistentDataTypes.UUID
                ) == uuid
            }
        }
        val new = item.get()
        for (slot in slots) {
            player.inventory.setItem(slot, new)
        }
        if (player.itemOnCursor.persistentDataContainer.get(AbilityItem.ABILITY_UUID, CustomPersistentDataTypes.UUID) == uuid) {
            player.setItemOnCursor(new)
        }
    }

    fun addNewAbility(ability: Ability<*, *>) {
        val assigned = ability.assignTo(this)
        AbilityManager.registerAbility(assigned)
        abilities.add(assigned)
        val player = player
        if (player != null) {
            for (item in assigned.items) {
                player.inventory.addItem(item.get())
            }
        }
    }

    fun isVented() = game.ventManager.isVented(this)

    fun hasAbility(ability: Ability<*, *>) = abilities.any { it.definition === ability }

    fun isInVent(): Boolean = game.ventManager.isNearVent(this)

    fun isInCams(): Boolean = game.cameraManager.isInCams(this)

    fun canSeeWhenLightsSabotage(): Boolean = false
}
