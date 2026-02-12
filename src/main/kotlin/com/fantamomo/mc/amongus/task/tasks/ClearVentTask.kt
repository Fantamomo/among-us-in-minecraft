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
import kotlin.random.Random

object ClearVentTask : Task<ClearVentTask, ClearVentTask.AssignedClearVentTask> { // not yet tested
    override val id: String = "clear_vent"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedClearVentTask(player)

    class AssignedClearVentTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<ClearVentTask, AssignedClearVentTask>() {

        override val task = ClearVentTask
        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.clear_vent.title"))

        private val leaf = itemStack(Material.OAK_LEAVES).hideTooltip().markWith("leaf")
        private val border = itemStack(Material.IRON_BARS).hideTooltip()

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (item.isMine() && item.isMarkedWith("leaf")) {
                inv.setItem(event.slot, null)
                checkCompletion()
            }
        }

        private fun checkCompletion() {
            val middleSlots = getMiddleItemSlots(SIZE)
            if (middleSlots.all { inv.getItem(it)?.isEmpty != true }) {
                player.game.taskManager.completeTask(this)
            }
        }

        override fun setupInventory() {
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }
            val middleSlots = getMiddleItemSlots(SIZE)
            middleSlots.forEach { slot ->
                if (Random.nextInt(100) < 60) {
                    inv.setItem(slot, leaf.clone())
                }
            }
            if (middleSlots.all { inv.getItem(it) == null }) {
                inv.setItem(middleSlots.random(), leaf.clone())
            }
        }

        companion object {
            const val SIZE = 45
        }
    }
}
