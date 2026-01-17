package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.task.areaLocation
import com.fantamomo.mc.amongus.util.hideTooltip
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object SwipeCardTask : Task<SwipeCardTask, SwipeCardTask.AssignedSwipeCardTask> {
    override val id: String = "swipe_card"
    override val type: TaskType = TaskType.COMMON

    override fun assignTo(player: AmongUsPlayer): AssignedSwipeCardTask? {
        if (player.game.area.tasks[id].isNullOrEmpty()) return null
        return AssignedSwipeCardTask(player)
    }

    class AssignedSwipeCardTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<SwipeCardTask, AssignedSwipeCardTask>() {

        override val task = SwipeCardTask
        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.swipe_card.title"))

        val border = (getBorderItemSlots(SIZE) as MutableList<Int>).apply { removeAt(9); removeAt(9) }

        private var opened = false

        override fun onInventoryClick(event: InventoryClickEvent) {
            check()
        }

        override fun onInventoryDrag(event: InventoryDragEvent) {
            check()
        }

        override fun tick() {
            if (!opened) return
            check()
        }

        override fun stop() {
            super.stop()
            opened = false
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            opened = false
        }

        private fun check() {
            for (slot in 9 until 18) {
                val stack = inv.getItem(slot) ?: return
                if (stack.type != Material.NAME_TAG) return
            }
            player.game.taskManager.completeTask(this)
        }

        override fun setupInventory() {
            opened = true
            val item = ItemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            for (slot in border) inv.setItem(slot, item)
            val stack = ItemStack(Material.NAME_TAG).hideTooltip().markAsMoveable()
            stack.amount = 9
            @Suppress("UnstableApiUsage")
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1)
            inv.setItem(9, stack)
        }

        companion object {
            const val SIZE = 27
        }
    }
}