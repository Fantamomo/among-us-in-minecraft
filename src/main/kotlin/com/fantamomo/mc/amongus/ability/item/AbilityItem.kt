package com.fantamomo.mc.amongus.ability.item

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.uuid.Uuid

/**
 * Represents an abstract item associated with an ability that can be assigned to a player.
 *
 * This class maintains a unique identifier (`uuid`) for each ability item and assigns it to
 * the player's persistent data container. It is responsible for creating and managing items
 * that are linked with specific abilities and handling changes to such items.
 *
 * @property ability The ability associated with this item, represented as an instance of [com.fantamomo.mc.amongus.ability.AssignedAbility].
 * @property id The unique identifier for the ability item, used for distinguishing it from others.
 */
abstract class AbilityItem(val ability: AssignedAbility<*, *>, val id: String) {
    /**
     * A unique identifier assigned to each ability item to distinguish it from others.
     *
     * This UUID is generated randomly when the ability item is created, ensuring that each
     * instance has a distinct and non-conflicting identifier. It is used primarily to
     * associate the ability item with the player, enabling
     * identification and proper tracking of the item during gameplay.
     */
    val uuid: Uuid = Uuid.random()

    protected abstract fun getItemStack(): ItemStack

    fun get(): ItemStack {
        val item = getItemStack()

        item.editPersistentDataContainer {
            it.set(ABILITY_UUID, CustomPersistentDataTypes.UUID, uuid)
            it.set(ABILITY_ID, PersistentDataType.STRING, ability.definition.id)
            it.set(GAME_UUID, CustomPersistentDataTypes.UUID, ability.player.game.uuid)
        }

        return item
    }

    abstract fun onRightClick()

    open fun onLeftClick() {}

    fun notifyItemChange() {
        ability.player.notifyAbilityItemChange(this)
    }

    companion object {
        val ABILITY_UUID = NamespacedKey(AmongUs, "ability/uuid")
        val ABILITY_ID = NamespacedKey(AmongUs, "ability/id")
        val GAME_UUID = NamespacedKey(AmongUs, "ability/game")
    }
}