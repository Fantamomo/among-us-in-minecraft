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

object FixWeatherNodeTask :
    Task<FixWeatherNodeTask, FixWeatherNodeTask.AssignedFixWeatherNodeTask> {

    override val id = "fix_weather_node"
    override val type = TaskType.LONG

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty() && !game.area.tasks[FIX_WEATHER_NODE_STAGE_2].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedFixWeatherNodeTask(player)

    class AssignedFixWeatherNodeTask(
        override val player: AmongUsPlayer
    ) : GuiAssignedTask<FixWeatherNodeTask, AssignedFixWeatherNodeTask>(), Steppable {

        override val task = FixWeatherNodeTask

        private val stage2Location =
            player.game.area.tasks[FIX_WEATHER_NODE_STAGE_2]?.random()
                ?: error("No stage 2 location")
        private val stage1Locations = player.game.area.tasks[id]?.sortedBy { it.distanceSquared(stage2Location) }?.take(stage2Levers.size) ?: error("No location for $id")
        private val stage1Location = stage1Locations.random()

        override var location: Location = stage1Location

        override var step = 0
        override val maxSteps = 2

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.fix_weather_node.title"))

        private val offItem = itemStack(Material.LEVER).hideTooltip().markWith("off")
        private val onItem = itemStack(Material.REDSTONE_TORCH).hideTooltip().markWith("on")
        private val backgroundItem = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val redItem = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip().markWith("off2")
        private val greenItem = itemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip().markWith("on2")

        override fun setupInventory() {
            repeat(SIZE) {
                inv.setItem(it, backgroundItem)
            }
            when (step) {
                0 -> setupStage1()
                1 -> setupStage2()
            }
        }

        private fun setupStage1() {
            leverSlots.forEach { inv.setItem(it, offItem) }
        }

        private fun setupStage2() {
            stage1Locations.forEachIndexed { index, loc ->
                val i = stage2Levers[index]
                if (loc == stage1Location) {
                    inv.setItem(i, offItem)
                    inv.setItem(i + 1, redItem)
                } else {
                    inv.setItem(i, onItem)
                    inv.setItem(i + 1, greenItem)
                }
            }
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val current = event.currentItem?.takeIf { it.isMine() } ?: return
            if (step == 0) {
                val slot = event.slot
                if (slot !in leverSlots) return
                val newItem =
                    if (current.isMarkedWith("off")) onItem
                    else if (current.isMarkedWith("on")) offItem
                    else return

                inv.setItem(slot, newItem)

                if (leverSlots.all { inv.getItem(it)?.isMarkedWith("on") == true }) {
                    location = stage2Location
                    player.game.taskManager.completeOneTaskStep(this)
                    step++
                    stop()
                }
            } else if (step == 1) {
                if (current.isMarkedWith("off2")) {
                    player.game.taskManager.completeTask(this)
                }
            }
        }

        companion object {
            const val SIZE = 45
            private val leverSlots = buildList {
                for (i in 9 until (SIZE - 9) step 9) {
                    addAll((i + 1 until i + 8) step 2)
                }
            }

            private val stage2Levers = buildList {
                for (i in 9 until (SIZE - 9) step 9) {
                    add(i + 1)
                    add(i + 6)
                }
            }
        }
    }

    const val FIX_WEATHER_NODE_STAGE_2 = "fix_weather_node_stage_2"
}
