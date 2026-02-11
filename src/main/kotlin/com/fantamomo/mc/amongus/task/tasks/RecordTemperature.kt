package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.*
import com.fantamomo.mc.amongus.util.hideTooltip
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import kotlin.random.Random

object RecordTemperature : Task<RecordTemperature, RecordTemperature.AssignedRecordTemperature> {
    override val id: String = "record_temperature"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer): AssignedRecordTemperature = AssignedRecordTemperature(player)

    class AssignedRecordTemperature(override val player: AmongUsPlayer) : GuiAssignedTask<RecordTemperature, AssignedRecordTemperature>() {
        override val task: RecordTemperature = RecordTemperature
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable(task.title))
        override val location: Location = areaLocation ?: error("No location for task $id")

        private var started = false
        private var currentTemp = Random.nextInt(32, 69)
        private val targetTemp = Random.nextInt(currentTemp - 30, currentTemp + 30)

        override fun setupInventory() {
            updateInventory()
        }

        @Suppress("UnstableApiUsage")
        private fun updateInventory() {
            inv.clear()
            val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            getBorderItemSlots(SIZE).forEach { inv.setItem(it, border) }

            val upButton = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip().markWith("up")
            val downButton = itemStack(Material.BLUE_STAINED_GLASS_PANE).hideTooltip().markWith("down")

            upButton.amount = currentTemp
            downButton.amount = currentTemp

            inv.setItem(11, downButton)
            inv.setItem(15, upButton)

            val display = itemStack(Material.PAPER)
            display.setData(DataComponentTypes.ITEM_NAME, textComponent(player.locale) {
                translatable("tasks.record_temperature.item.status") {
                    args {
                        numeric("current", currentTemp)
                        numeric("target", targetTemp)
                    }
                }
            })
            display.amount = targetTemp
            inv.setItem(13, display)
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem?.takeIf { it.isMine() } ?: return
            when (item.getCustomId()) {
                "up" -> currentTemp++
                "down" -> currentTemp--
                else -> return
            }
            
            if (currentTemp == targetTemp) {
                player.game.taskManager.completeTask(this)
            } else {
                updateInventory()
            }
        }

        override fun state(): TaskState? = if (started) TaskState.IN_PROGRESS else null

        companion object {
            const val SIZE = 27
        }
    }
}