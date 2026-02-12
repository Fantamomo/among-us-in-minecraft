package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Steppable
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import kotlin.time.Duration.Companion.seconds

object FuelEnginesTask : Task<FuelEnginesTask, FuelEnginesTask.AssignedFuelEnginesTask> {
    override val id: String = "fuel_engines"
    override val type: TaskType = TaskType.LONG

    const val REFUEL_STATION = "refuel_station"

    override fun isAvailable(game: Game) = 
        !game.area.tasks[id].isNullOrEmpty() && !game.area.tasks[REFUEL_STATION].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedFuelEnginesTask(player)

    class AssignedFuelEnginesTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<FuelEnginesTask, AssignedFuelEnginesTask>(), Steppable {

        override val task = FuelEnginesTask
        private val refuelLocation = player.game.area.tasks[REFUEL_STATION]?.random() ?: throw IllegalArgumentException("No refuel location for task $id")
        private val engineLocations = player.game.area.tasks[id]?.let { locations ->
            if (locations.size >= 2) locations.shuffled().take(2)
            else if (locations.isNotEmpty()) listOf(locations.first(), locations.first())
            else null
        } ?: throw IllegalArgumentException("No engine location for task $id")
        
        override var location: Location = refuelLocation
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.fuel_engines.title"))

        override var step: Int = 0
        override val maxSteps: Int = 4

        private val cooldown = Cooldown(3.seconds)
        private val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val emptyBucket = itemStack(Material.BUCKET).hideTooltip().markWith("empty")
        private val fullBucket = itemStack(Material.LAVA_BUCKET).hideTooltip().markWith("full")

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (item.isMine()) {
                if (item.isMarkedWith("empty") || item.isMarkedWith("full")) {
                    if (!cooldown.isRunning()) {
                        cooldown.start()
                    }
                }
            }
        }

        override fun tick() {
            if (cooldown.isFinished()) {
                cooldown.reset()
                if (step < maxSteps) {
                    location = if (step % 2 == 1) refuelLocation else engineLocations[step / 2]
                    stop()
                    player.game.taskManager.completeOneTaskStep(this)
                    step++
                } else {
                    player.game.taskManager.completeTask(this)
                }
                return
            }
            if (cooldown.isRunning()) {
                val progress = 1f - (cooldown.remaining() / cooldown.startDuration())
                val middleSlots = getMiddleItemSlots(SIZE)
                val filledCount = (progress * middleSlots.size).toInt()
                middleSlots.forEachIndexed { index, slot ->
                    if (index < filledCount) {
                        inv.setItem(slot, itemStack(Material.ORANGE_STAINED_GLASS_PANE).hideTooltip())
                    }
                }
            }
        }

        override fun setupInventory() {
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }
            val grayPane = itemStack(Material.GRAY_STAINED_GLASS_PANE).hideTooltip()
            getMiddleItemSlots(SIZE).forEach { inv.setItem(it, grayPane) }
            if (step % 2 == 0) {
                inv.setItem(13, emptyBucket)
            } else {
                inv.setItem(13, fullBucket)
            }
        }

        companion object {
            const val SIZE = 27
        }
    }
}
