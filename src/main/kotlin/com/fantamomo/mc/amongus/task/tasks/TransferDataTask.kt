package com.fantamomo.mc.amongus.task.tasks

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

object TransferDataTask : Task<TransferDataTask, TransferDataTask.AssignedTransferDataTask> {
    override val id: String = "transfer_data"
    override val type: TaskType = TaskType.LONG

    override fun assignTo(player: AmongUsPlayer): AssignedTransferDataTask? {
        if (player.game.area.tasks[id].isNullOrEmpty()) return null
        if (player.game.area.tasks[UPLOAD_DATA].isNullOrEmpty()) return null
        return AssignedTransferDataTask(player)
    }

    class AssignedTransferDataTask(override val player: AmongUsPlayer) : GuiAssignedTask<TransferDataTask, AssignedTransferDataTask>(), Steppable {
        override val task = TransferDataTask
        override var location: Location = player.game.area.tasks[id]?.random() ?: throw IllegalArgumentException("No location for task $id")
        private val uploadLocation = player.game.area.tasks[UPLOAD_DATA]?.random() ?: throw IllegalArgumentException("No upload location for task $id")
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable("tasks.transfer_data.title"))

        private val duration = 7.seconds + (1..10).random().seconds
        private val countdown = Cooldown(duration)
        private var ticks = -1
        private var uploadSlot = 0

        private val backgroundItem = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val greenItem = itemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip()
        private val lightBlueItem = itemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).hideTooltip()
        private val grayItem = itemStack(Material.GRAY_STAINED_GLASS_PANE).hideTooltip()

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (item.isMine() && item.isMarkedWith("start")) {
                countdown.start()
            }
        }

        override fun setupInventory() {
            for (slot in 0 until SIZE) {
                inv.setItem(slot, backgroundItem)
            }
            val startItem = itemStack(Material.GREEN_STAINED_GLASS_PANE).hideTooltip().markWith("start")
            inv.setItem(13, startItem)
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            countdown.reset()
            ticks = -1
            uploadSlot = 0
        }

        override fun tick() {
            if (!countdown.isRunning() && !countdown.isFinished()) return
            if (countdown.isFinished()) {
                location = uploadLocation
                player.game.taskManager.completeOneTaskStep(this)
                step++
                stop()
                countdown.reset()
                ticks = -1
                uploadSlot = 0
                return
            }
            ticks++
            if (ticks % 2 == 0) {
                inv.setItem(borderSlots[uploadSlot], backgroundItem)
                uploadSlot = (uploadSlot + 1) % borderSlots.size
                inv.setItem(borderSlots[uploadSlot], greenItem)
            }
            if (ticks % 10 == 0) {
                val progress = 1f - (countdown.remaining() / countdown.startDuration())
                val size = middleSlots.size
                for (slot in 0 until size) {
                    val f = slot / size.toFloat()
                    inv.setItem(middleSlots.first() + slot, if (f <= progress) lightBlueItem else grayItem)
                }
            }
        }

        override var step: Int = 0
        override val maxSteps: Int = 2

        companion object {
            const val SIZE = 27
            private val borderSlots = buildList {
                addAll(0 until 9)
                add(17)
                addAll(26 downTo 18)
                add(9)
            }
            private val middleSlots = getMiddleItemSlots(SIZE)
        }
    }

    const val UPLOAD_DATA = "upload_data"
}