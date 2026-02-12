package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Steppable
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

object DivertPowerTask : Task<DivertPowerTask, DivertPowerTask.AssignedDivertPowerTask> { // not yet tested
    override val id: String = "divert_power"
    override val type: TaskType = TaskType.SHORT

    const val ACCEPT_POWER = "accept_power"

    override fun isAvailable(game: Game) = 
        !game.area.tasks[id].isNullOrEmpty() && !game.area.tasks[ACCEPT_POWER].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedDivertPowerTask(player)

    class AssignedDivertPowerTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<DivertPowerTask, AssignedDivertPowerTask>(), Steppable {

        override val task = DivertPowerTask
        override var location: Location = player.game.area.tasks[id]?.random() ?: throw IllegalArgumentException("No location for task $id")
        private val acceptLocation = player.game.area.tasks[ACCEPT_POWER]?.random() ?: throw IllegalArgumentException("No accept location for task $id")
        
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.divert_power.title"))

        override var step: Int = 0
        override val maxSteps: Int = 2

        private val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val leverOff = itemStack(Material.GRAY_DYE).hideTooltip().markWith("lever_off")
        private val leverOn = itemStack(Material.LIME_DYE).hideTooltip().markWith("lever_on")

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (item.isMine()) {
                if (step == 0 && item.isMarkedWith("lever_off")) {
                    inv.setItem(event.slot, leverOn.clone())
                    player.game.taskManager.completeOneTaskStep(this)
                    step++
                    location = acceptLocation
                    stop()
                } else if (step == 1 && item.isMarkedWith("accept")) {
                    player.game.taskManager.completeTask(this)
                }
            }
        }

        override fun setupInventory() {
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }
            if (step == 0) {
                val randomSlot = getMiddleItemSlots(SIZE).random()
                inv.setItem(randomSlot, leverOff.clone())
            } else {
                val acceptItem = itemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE).hideTooltip().markWith("accept")
                inv.setItem(13, acceptItem)
            }
        }

        companion object {
            const val SIZE = 27
        }
    }
}
