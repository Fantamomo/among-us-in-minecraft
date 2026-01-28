package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.*
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import kotlin.math.absoluteValue
import kotlin.random.Random

object ClearAsteroidsTask : Task<ClearAsteroidsTask, ClearAsteroidsTask.AssignedClearAsteroidsTask> {
    override val id: String = "clear_asteroids"
    override val type: TaskType = TaskType.LONG

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedClearAsteroidsTask(player)

    class AssignedClearAsteroidsTask(override val player: AmongUsPlayer) : GuiAssignedTask<ClearAsteroidsTask, AssignedClearAsteroidsTask>() {
        override val task = ClearAsteroidsTask
        override val location: Location = areaLocation ?: error("No Location for $id")
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable("tasks.clear_asteroids.title"))

        private val backgroundItem = itemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).hideTooltip()
        private val asteroidsItem = itemStack(Material.FIREWORK_STAR).hideTooltip().markWith("asteroid")
        private val shootItem = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip()
        private val countItem = itemStack(Material.REDSTONE).hideTooltip()

        private val asteroids: MutableMap<Int, Int> = mutableMapOf()
        private var shootAsteroids = 0
        private var open = false
        private var ticks = -1

        override fun onInventoryClick(event: InventoryClickEvent) {
            val slot = event.slot
            if (slot !in asteroids) return
            val item = event.currentItem ?: return
            if (item.isMine() && item.isMarkedWith("asteroid")) {
                inv.setItem(slot, shootItem)
                asteroids[slot] = -ticks - 15
                shootAsteroids++
            }
            countItem.amount = (NEEDED_ASTEROIDS - shootAsteroids).coerceAtLeast(1)
            inv.setItem(SIZE - 1, countItem)
            if (shootAsteroids >= NEEDED_ASTEROIDS) {
                player.game.taskManager.completeTask(this)
            }
        }

        override fun setupInventory() {
            open = true
            for (slot in 0 until SIZE) {
                inv.setItem(slot, backgroundItem)
            }
            countItem.amount = (NEEDED_ASTEROIDS - shootAsteroids).coerceAtLeast(1)
            inv.setItem(SIZE - 1, countItem)
        }

        override fun onInventoryClose() {
            open = false
            ticks = 0
            asteroids.clear()
        }

        override fun state(): TaskState? = TaskState.IN_PROGRESS.takeIf { shootAsteroids > 0 }

        override fun tick() {
            if (!open) return
            ticks++
            if (ticks % 4 == 0) {
                if (Random.nextFloat() < 0.2) {
                    val target = (0 until SIZE - 1).random()
                    if (target !in asteroids) {
                        inv.setItem(target, asteroidsItem)
                        asteroids[target] = ticks + SHOW_FRAME
                    }
                }
                for (entry in asteroids) {
                    if (entry.value.absoluteValue < ticks) {
                        inv.setItem(entry.key, backgroundItem)
                    }
                }
                asteroids.entries.removeIf { entry -> entry.value.absoluteValue < ticks }
            }
        }

        companion object {
            const val SIZE = 54
            const val NEEDED_ASTEROIDS = 20
            const val SHOW_FRAME = 25
        }
    }
}