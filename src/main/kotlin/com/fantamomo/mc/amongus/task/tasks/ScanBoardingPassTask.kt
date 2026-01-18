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
import org.bukkit.inventory.ItemStack

object ScanBoardingPassTask : Task<ScanBoardingPassTask, ScanBoardingPassTask.AssignedScanBoardingPassTask> {
    override val id: String = "scan_boarding_pass"
    override val type: TaskType = TaskType.COMMON

    override fun assignTo(player: AmongUsPlayer): AssignedScanBoardingPassTask? {
        if (player.game.area.tasks[id].isNullOrEmpty()) return null
        return AssignedScanBoardingPassTask(player)
    }

    class AssignedScanBoardingPassTask(override val player: AmongUsPlayer) : GuiAssignedTask<ScanBoardingPassTask, AssignedScanBoardingPassTask>() {
        override val task = ScanBoardingPassTask
        override val location: Location = areaLocation ?: error("No location found for $id")
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable("tasks.scan_boarding_pass.title"))

        private var ticks = -1
        private var status = -1

        override fun onInventoryClick(event: InventoryClickEvent) {
            if (status > -1) return
            if (event.slot != TARGET_SLOT) return
            val item = event.cursor
            if (item.type != Material.FILLED_MAP) return
            status = 0
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            ticks = -1
            status = -1
        }

        override fun tick() {
            if (status == -1) return
            ticks++
            if (ticks % 2 == 0) {
                if (inv.getItem(TARGET_SLOT)?.type != Material.FILLED_MAP) {
                    val statusItem = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).hideTooltip()
                    inv.setItem(statusSlots[status], statusItem)
                    status--
                    return
                }
            }
            if (ticks % 20 == 0) {
                if (status == statusSlots.size) {
                    player.game.taskManager.completeTask(this)
                    return
                }
                val greenItem = ItemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip()
                inv.setItem(statusSlots[status], greenItem)
                status++
            }
        }

        override fun setupInventory() {
            val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            for (slot in 0 until SIZE) {
                inv.setItem(slot, background)
            }
            val statusItem = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).hideTooltip()
            for (slot in statusSlots) {
                inv.setItem(slot, statusItem)
            }
            val boardingPassItem = ItemStack(Material.FILLED_MAP).hideTooltip().markAsMoveable()
            inv.setItem(BOARDING_PASS_SLOT, boardingPassItem)
            inv.setItem(TARGET_SLOT, null)
        }

        companion object {
            const val SIZE = 45
            const val BOARDING_PASS_SLOT = 20
            const val TARGET_SLOT = 24
            val statusSlots = listOf(15, 16, 25, 34, 33, 32, 23, 14)
        }
    }
}