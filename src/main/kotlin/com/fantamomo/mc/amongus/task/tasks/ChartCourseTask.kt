package com.fantamomo.mc.amongus.task.tasks

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
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

object ChartCourseTask : Task<ChartCourseTask, ChartCourseTask.AssignedChartCourseTask> {
    override val id: String = "chart_course"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedChartCourseTask(player)

    class AssignedChartCourseTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<ChartCourseTask, AssignedChartCourseTask>() {

        override val task = ChartCourseTask
        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.chart_course.title"))

        private val ship = itemStack(Material.MINECART).hideTooltip().markWith("ship")
        private val point = itemStack(Material.FIREWORK_STAR).hideTooltip().markWith("point")
        private val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        
        private val courseSlots = listOf(10, 12, 14, 16)
        private var currentPointIndex = 0

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (item.isMine() && item.isMarkedWith("point")) {
                val clickedSlot = event.slot
                if (clickedSlot == courseSlots[currentPointIndex]) {
                    inv.setItem(clickedSlot, ship.clone())
                    if (currentPointIndex > 0) {
                        inv.setItem(courseSlots[currentPointIndex - 1], null)
                    } else {
                        inv.setItem(9, null)
                    }
                    
                    currentPointIndex++
                    if (currentPointIndex >= courseSlots.size) {
                        player.game.taskManager.completeTask(this)
                    }
                }
            }
        }

        override fun setupInventory() {
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }
            inv.setItem(9, ship.clone())
            courseSlots.forEach { inv.setItem(it, point.clone()) }
        }

        companion object {
            const val SIZE = 27
        }
    }
}
