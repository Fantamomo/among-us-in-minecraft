package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.task.areaLocation
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import kotlin.random.Random

object NumbersTask : Task<NumbersTask, NumbersTask.AssignedNumbersTask> {
    override val id: String = "numbers"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedNumbersTask(player)

    class AssignedNumbersTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<NumbersTask, AssignedNumbersTask>() {
        override val task = NumbersTask
        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")

        private val borderItemSlots = getBorderItemSlots(SIZE)
        private val middleItemSlots = getMiddleItemSlots(SIZE)

        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable("tasks.numbers.title"))

        private var currentNumber = 0
        private var ticks = 0
        private var until = -1

        override fun onInventoryClick(event: InventoryClickEvent) {
            if (until > ticks) return
            val slot = event.slot
            if (slot !in middleItemSlots) return
            val currentItem = event.currentItem ?: return
            if (!currentItem.isMine() || !currentItem.isMarkedWith("number")) return
            val i = currentItem.persistentDataContainer.get(NUMBER_KEY, PersistentDataType.INTEGER) ?: return
            if (i == currentNumber) {
                currentNumber++
                val stack = currentItem.withType(Material.LIME_STAINED_GLASS_PANE)
                inv.setItem(slot, stack)
            } else {
                until = ticks + ERROR_TIME
                currentNumber = 0
            }
            if (currentNumber == middleItemSlots.size) player.game.taskManager.completeTask(this)
        }

        override fun setupInventory() {
            val background = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            borderItemSlots.forEach { slot -> inv.setItem(slot, background) }
            val middleItemSlots = middleItemSlots
            middleItemSlots.sortedBy { Random.nextInt() }.forEachIndexed { index, slot ->
                val stack = itemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).markWith("number")
                stack.amount = index + 1
                stack.editPersistentDataContainer {
                    it.set(NUMBER_KEY, PersistentDataType.INTEGER, index)
                }
                stack.hideTooltip()
                inv.setItem(slot, stack)
            }
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            currentNumber = 0
            until = -1
        }

        override fun tick() {
            if (until > ticks) {
                val diff = until - ticks
                if (diff % FRAME_TIME == 0) {
                    for (slot in middleItemSlots) {
                        val item = inv.getItem(slot)?.withType(Material.RED_STAINED_GLASS_PANE)
                        inv.setItem(slot, item)
                    }
                } else if (diff % (FRAME_TIME / 2) == 0) {
                    for (slot in middleItemSlots) {
                        val item = inv.getItem(slot)?.withType(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                        inv.setItem(slot, item)
                    }
                }
            }
            ticks++
        }

        companion object {
            private const val SIZE = 36
            private const val FRAME_TIME = 10
            private const val ERROR_TIME = FRAME_TIME * 4
            private val NUMBER_KEY = NamespacedKey(AmongUs, "task/number/id")
        }
    }
}