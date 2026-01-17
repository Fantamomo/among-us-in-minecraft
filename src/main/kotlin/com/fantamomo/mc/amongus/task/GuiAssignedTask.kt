package com.fantamomo.mc.amongus.task

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

abstract class GuiAssignedTask<T : Task<T, A>, A : GuiAssignedTask<T, A>> : AssignedTask<T, A>, InventoryHolder {
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

    open fun setupInventory() {}

    open fun onInventoryClose() {
        inv.clear()
    }

    abstract fun onInventoryClick(event: InventoryClickEvent)

    companion object {
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