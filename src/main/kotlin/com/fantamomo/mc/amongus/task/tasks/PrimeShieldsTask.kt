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

object PrimeShieldsTask : Task<PrimeShieldsTask, PrimeShieldsTask.AssignedPrimeShieldsTask> { // not yet tested
    override val id: String = "prime_shields"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedPrimeShieldsTask(player)

    class AssignedPrimeShieldsTask(override val player: AmongUsPlayer) :
        GuiAssignedTask<PrimeShieldsTask, AssignedPrimeShieldsTask>() {

        override val task = PrimeShieldsTask
        override val location: Location = areaLocation ?: throw IllegalArgumentException("No location for task $id")
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.prime_shields.title"))

        private val redShield = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip().markWith("shield_red")
        private val whiteShield = itemStack(Material.WHITE_STAINED_GLASS_PANE).hideTooltip().markWith("shield_white")
        private val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (item.isMine() && item.isMarkedWith("shield_red")) {
                inv.setItem(event.slot, whiteShield.clone())
                checkCompletion()
            }
        }

        private fun checkCompletion() {
            val shields = shieldSlots.map { inv.getItem(it) }
            if (shields.all { it != null && it.isMarkedWith("shield_white") }) {
                player.game.taskManager.completeTask(this)
            }
        }

        override fun setupInventory() {
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }
            val shuffled = shieldSlots.shuffled()
            val shields = Random.nextInt(1, shuffled.size)
            shuffled.take(shields).forEach { slot ->
                inv.setItem(slot, redShield.clone())
            }
        }

        companion object {
            const val SIZE = 27
            val shieldSlots = listOf(10, 12, 14, 16, 4, 22, 13)
        }
    }
}
