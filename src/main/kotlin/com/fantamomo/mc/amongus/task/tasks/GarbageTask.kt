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

object GarbageTask : Task<GarbageTask, GarbageTask.AssignedGarbageTask> {
    override val id: String = "garbage"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedGarbageTask(player)

    class AssignedGarbageTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<GarbageTask, AssignedGarbageTask>() {

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.garbage.title"))
        val borderItemSlots = getBorderItemSlots(SIZE)
        val middleItemSlots = getMiddleItemSlots(SIZE)

        override val task = GarbageTask

        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")

        override fun onInventoryClick(event: InventoryClickEvent) {
            val slot = event.slot
            if (slot in borderItemSlots) return
            if (slot !in middleItemSlots) return
            event.isCancelled = false
            event.currentItem = null
            inv.setItem(slot, null)
            if (middleItemSlots.all { slot -> inv.getItem(slot).let { it == null || it.isEmpty } }) {
                player.game.taskManager.completeTask(this)
            }
        }

        override fun setupInventory() {
            val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            borderItemSlots.forEach { slot ->
                inv.setItem(slot, border)
            }
            middleItemSlots.forEach { slot ->
                if (Random.nextBoolean()) {
                    inv.setItem(slot, itemStack(garbage.random()).hideTooltip())
                }
            }
            middleItemSlots.random().let { inv.setItem(it, itemStack(garbage.random()).hideTooltip()) }
        }

        companion object {
            const val SIZE = 36
            val garbage: List<Material> = listOf(
                Material.DIRT,
                Material.DIAMOND,
                Material.COAL,
                Material.GUNPOWDER,
                Material.DEAD_BUSH,
                Material.STRING,
                Material.DEAD_FIRE_CORAL_FAN,
                Material.GOLD_INGOT
            )
        }
    }
}