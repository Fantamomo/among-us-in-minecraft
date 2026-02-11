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

object FillCanister : Task<FillCanister, FillCanister.AssignedFillCanister> {
    override val id: String = "fill_canister"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer): AssignedFillCanister = AssignedFillCanister(player)

    class AssignedFillCanister(override val player: AmongUsPlayer) : GuiAssignedTask<FillCanister, AssignedFillCanister>() {
        override val task: FillCanister = FillCanister
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable(task.title))
        override val location: Location = areaLocation ?: error("No location for task $id")

        private var progress = 0
        private val maxProgress = 5

        override fun setupInventory() {
            updateInventory()
        }

        private fun updateInventory() {
            val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }

            val fillButton = itemStack(Material.LEVER).hideTooltip().markWith("fill_button")
            inv.setItem(22, fillButton)

            for (i in 0 until maxProgress) {
                val material = if (i < progress) Material.YELLOW_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE
                inv.setItem(11 + i, itemStack(material).hideTooltip())
            }
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem?.takeIf { it.isMine() } ?: return
            if (item.isMarkedWith("fill_button")) {
                progress++
                if (progress >= maxProgress) {
                    player.game.taskManager.completeTask(this)
                } else {
                    updateInventory()
                }
            }
        }

        override fun state(): TaskState? = if (progress > 0) TaskState.IN_PROGRESS else null

        companion object {
            const val SIZE = 36
        }
    }
}