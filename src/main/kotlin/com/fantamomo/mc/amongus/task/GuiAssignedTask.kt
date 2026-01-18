package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.uuid.Uuid

abstract class GuiAssignedTask<T : Task<T, A>, A : GuiAssignedTask<T, A>> : AssignedTask<T, A>, InventoryHolder {
    abstract override val location: Location
    abstract override val task: T
    val uuid = Uuid.random()
    protected abstract val inv: Inventory

    override fun getInventory() = inv

    override fun stop() {
        inv.close()
        inv.clear()
    }

    override fun start() {
        player.player?.openInventory(inv)
        setupInventory()
    }

    abstract fun setupInventory()

    open fun onInventoryClose() {
        inv.clear()
    }

    abstract fun onInventoryClick(event: InventoryClickEvent)

    protected fun ItemStack.markAsMoveable(): ItemStack = apply {
        editPersistentDataContainer {
            it.set(MOVEABLE_ITEM_KEY, PersistentDataType.BYTE, 1)
        }
    }

    protected fun itemStack(material: Material): ItemStack = ItemStack(material).apply {
        editPersistentDataContainer {
            it.set(TASK_UUID, CustomPersistentDataTypes.UUID, uuid)
        }
    }

    protected fun ItemStack.isMine(): Boolean = persistentDataContainer.run { has(TASK_UUID, CustomPersistentDataTypes.UUID) && get(TASK_UUID, CustomPersistentDataTypes.UUID) == uuid }

    protected fun ItemStack.markWith(id: String): ItemStack = apply {
        editPersistentDataContainer {
            it.set(ITEM_STACK_CUSTOM_ID, PersistentDataType.STRING, id)
        }
    }

    protected fun ItemStack.getCustomId(): String? = persistentDataContainer.get(ITEM_STACK_CUSTOM_ID, PersistentDataType.STRING)

    protected fun ItemStack.isMarkedWith(id: String): Boolean = getCustomId() == id

    open fun onInventoryDrag(event: InventoryDragEvent) {}

    companion object {
        val MOVEABLE_ITEM_KEY = NamespacedKey(AmongUs, "task_item/moveable")
        val TASK_UUID = NamespacedKey(AmongUs, "task_item/uuid")
        val ITEM_STACK_CUSTOM_ID = NamespacedKey(AmongUs, "task_item/custom_id")

        fun getMiddleItemSlots(size: Int): List<Int> {
            val result = mutableListOf<Int>()
            val rows = size / 9

            for (slot in 0 until size) {
                val row = slot / 9
                val col = slot % 9

                val isBorder =
                    row == 0 ||
                            row == rows - 1 ||
                            col == 0 ||
                            col == 8

                if (!isBorder) {
                    result.add(slot)
                }
            }
            return result
        }

        fun getBorderItemSlots(size: Int): List<Int> {
            val result = mutableListOf<Int>()
            val rows = size / 9

            for (slot in 0 until size) {
                val row = slot / 9
                val col = slot % 9

                val isBorder =
                    row == 0 ||
                            row == rows - 1 ||
                            col == 0 ||
                            col == 8

                if (isBorder) {
                    result.add(slot)
                }
            }

            return result
        }
    }

}