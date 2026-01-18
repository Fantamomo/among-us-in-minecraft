package com.fantamomo.mc.amongus.task.tasks

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
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

object InsertKeyTask : Task<InsertKeyTask, InsertKeyTask.AssignedInsertKeyTask> {
    override val id: String = "insert_key"
    override val type: TaskType = TaskType.COMMON

    override fun assignTo(player: AmongUsPlayer): AssignedInsertKeyTask? {
        if (player.game.area.tasks[id].isNullOrEmpty()) return null
        return AssignedInsertKeyTask(player)
    }

    class AssignedInsertKeyTask(override val player: AmongUsPlayer) : GuiAssignedTask<InsertKeyTask, AssignedInsertKeyTask>() {
        override val task = InsertKeyTask
        override val location: Location = areaLocation ?: error("No location found for $id")
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable(""))
        private val target = targetSlots.random()

        override fun onInventoryClick(event: InventoryClickEvent) {
            val slot = event.slot
            if (slot != target) return
            val cursor = event.cursor
            if (!cursor.isMine() || !cursor.isMarkedWith("key") || cursor.type != Material.TRIAL_KEY) return
            event.isCancelled = true
            player.game.taskManager.completeTask(this)
        }

        override fun setupInventory() {
            val background = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            for (slot in 0 until SIZE) {
                inv.setItem(slot, background)
            }
            val key = itemStack(Material.TRIAL_KEY).hideTooltip().markAsMoveable().markWith("key")
            inv.setItem(40, key)
            val red = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip()
            val green = itemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip()
            for (slot in targetSlots) {
                inv.setItem(slot, if (slot == target) green else red)
            }
        }

        companion object {
            const val SIZE = 45
            val targetSlots = listOf(
                10, 12, 14, 16,
                19, 21, 23, 25,
                28, 30, 32, 34
            )
        }
    }
}