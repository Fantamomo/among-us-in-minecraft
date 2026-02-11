package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType

object OpenWaterwaysTask : Task<OpenWaterwaysTask, OpenWaterwaysTask.AssignedOpenWaterwaysTask> {
    override val id: String = "open_waterways"
    override val type: TaskType = TaskType.LONG

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedOpenWaterwaysTask(player)

    class AssignedOpenWaterwaysTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<OpenWaterwaysTask, AssignedOpenWaterwaysTask>() {
        override val task = OpenWaterwaysTask
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.open_waterways.title"))
        override val location: Location = player.game.area.tasks[id]?.randomOrNull() ?: error("No Location for $id")

        private val grayItem = itemStack(Material.GRAY_STAINED_GLASS_PANE).hideTooltip()
        private val backgroundItem = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val redItem = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip().markWith("not_filling")
        private val greenItem = itemStack(Material.GREEN_STAINED_GLASS_PANE).hideTooltip().markWith("filling")
        private val waterItem = itemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).hideTooltip()
        private val cyanItem = itemStack(Material.CYAN_STAINED_GLASS_PANE).hideTooltip()

        private var open = false
        private var tick = 0

        private var waterLevel = 0
        private var fallingWaterCol = -1
        private var fallingWaterRow = -1
        private var fillingIn = 0

        override fun setupInventory() {
            open = true
            for (row in 0 until ROWS) {
                for (col in 0 until 7) {
                    val index = row * 9 + col

                    val item = when {
                        row == fallingWaterRow && col == fallingWaterCol -> cyanItem
                        (ROWS - 1 - row) * 7 + col < waterLevel -> waterItem
                        else -> grayItem
                    }
                    inv.setItem(index, item)
                }
                inv.setItem(row * 9 + 7, backgroundItem)
                val stack = if (ROWS - fillingIn <= row) greenItem else redItem
                stack.editPersistentDataContainer {
                    it.set(FILLING_ITEM_KEY, PersistentDataType.INTEGER, ROWS - row)
                }
                inv.setItem(row * 9 + 8, stack)
            }
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (!item.isMine()) return
            val filling = item.persistentDataContainer.get(FILLING_ITEM_KEY, PersistentDataType.INTEGER) ?: return
            if (item.isMarkedWith("filling")) {
                fillingIn = filling - 1
                return
            }
            if (fillingIn + 1 == filling) {
                fillingIn++
                setupInventory()
            }
        }

        override fun tick() {
            if (fillingIn == 0 || !open) return
            if (waterLevel >= REQUIRED_WATER) {
                player.game.taskManager.completeTask(this)
                return
            }
            tick++

            val delay = maxOf(2, (ROWS + 1 - fillingIn) * 2)

            if (fallingWaterRow == -1 && tick % delay == 0) {
                fallingWaterCol = (0 until 7).random()
                fallingWaterRow = 0
                setupInventory()
            }

            if (fallingWaterRow >= 0 && tick % 2 == 0) {
                val targetRow = ROWS - 1 - (waterLevel / 7)

                if (fallingWaterRow < targetRow) {
                    fallingWaterRow++
                    setupInventory()
                } else {
                    waterLevel++
                    fallingWaterRow = -1
                    fallingWaterCol = -1
                    setupInventory()
                }
            }
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            open = false
        }

        companion object {
            const val ROWS = 6
            const val SIZE = 9 * ROWS
            const val REQUIRED_WATER = ROWS * 7

            private val FILLING_ITEM_KEY = NamespacedKey(AmongUs, "task/open_waterways/filling")
        }
    }
}