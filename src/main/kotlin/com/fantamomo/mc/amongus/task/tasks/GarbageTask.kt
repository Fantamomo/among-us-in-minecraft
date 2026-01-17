package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object GarbageTask : Task<GarbageTask, GarbageTask.AssignedGarbageTask> {
    override val id: String = "garbage"
    override val type: TaskType = TaskType.SHORT

    override fun assignTo(player: AmongUsPlayer): AssignedGarbageTask? {
        if (player.game.area.tasks[id].isNullOrEmpty()) {
            return null
        }
        return AssignedGarbageTask(player)
    }

    class AssignedGarbageTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<GarbageTask, AssignedGarbageTask>() {

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("task.title.garbage"))
        val borderItemSlots = getBorderItemSlots(SIZE)
        val middleItemSlots = getMiddleItemSlots(SIZE)

        override val task = GarbageTask

        override val location: Location = player.game.area.tasks[task.id]?.firstOrNull() ?: throw IllegalArgumentException("No location for task $id")

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
            val border = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            borderItemSlots.forEach { slot ->
                inv.setItem(slot, border)
            }
            middleItemSlots.forEach { slot ->
                if (Random.nextBoolean()) {
                    inv.setItem(slot, ItemStack(garbage.random()))
                }
            }
            middleItemSlots.random().let { inv.setItem(it, ItemStack(garbage.random())) }
        }

        companion object {
            const val SIZE = 36
            val garbage: List<Material> = listOf(
                Material.DIRT,
                Material.DIAMOND,
                Material.COAL_ORE,
                Material.DEAD_BUSH,
                Material.STRING,
                Material.GUNPOWDER
            )
        }
    }
}