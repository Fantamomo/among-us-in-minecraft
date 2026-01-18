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
import kotlin.random.Random

object RepairDrillTask : Task<RepairDrillTask, RepairDrillTask.AssignedRepairDrillTask> {
    override val id: String = "repair_drill"
    override val type: TaskType = TaskType.SHORT

    override fun assignTo(player: AmongUsPlayer): AssignedRepairDrillTask? {
        if (player.game.area.tasks[id].isNullOrEmpty()) return null
        return AssignedRepairDrillTask(player)
    }

    class AssignedRepairDrillTask(override val player: AmongUsPlayer) : GuiAssignedTask<RepairDrillTask, AssignedRepairDrillTask>() {
        override val task = RepairDrillTask
        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable("tasks.repair_drill.title"))

        private val availableTargets = MutableList(SIZE) { it }.apply { shuffle() }

        private val targets = List(Random.nextInt(SIZE / 4, (SIZE / 4) * 3)) { availableTargets[it] }.also { availableTargets.clear() }.associateWithTo(mutableMapOf()) { 4 }

        private val background = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val red = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip().apply { amount = 4 }
        private val green = itemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip()

        override fun onInventoryClick(event: InventoryClickEvent) {
            event.currentItem?.takeIf { it.isMine() } ?: return
            val slot = event.slot
            if (slot !in targets) return
            val amount = targets[slot]!! - 1
            if (amount == 0) {
                targets.remove(slot)
                inv.setItem(slot, green)
            } else {
                targets[slot] = amount
                inv.setItem(slot, red.clone().also { it.amount = amount })
            }
            if (targets.isEmpty()) player.game.taskManager.completeTask(this)
        }

        override fun setupInventory() {
            for (slot in 0 until SIZE)
                inv.setItem(slot, background)
            for (slot in targets.keys) inv.setItem(slot, red)
        }

        companion object {
            const val SIZE = 45
        }
    }
}