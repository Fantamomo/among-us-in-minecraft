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
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object StartReaktorTask : Task<StartReaktorTask, StartReaktorTask.AssignedStartReaktorTask> {

    override val id = "start_reaktor"
    override val type = TaskType.LONG

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedStartReaktorTask(player)

    class AssignedStartReaktorTask(
        override val player: AmongUsPlayer
    ) : GuiAssignedTask<StartReaktorTask, AssignedStartReaktorTask>() {

        override val task = StartReaktorTask
        override val location: Location =
            areaLocation ?: error("No location for task $id")

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.start_reaktor.title"))

        private val leftGrid = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
        private val rightGrid = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)

        private var path = mutableListOf<Int>()

        private var phase = Phase.SHOW
        private var ticks = 0

        private var step = 1
        private var showIndex = 0
        private var inputIndex = 0
        private var blinkOn = false

        private var greenTicksRemaining = 0
        private var lastGreenSlot = -1
        private val greenDuration = 10

        override fun setupInventory() {
            generateNewPath()
            drawBackground()
            drawFrames()
            resetState()
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            if (phase != Phase.INPUT) return

            val index = rightGrid.indexOf(event.slot)
            if (index == -1) return

            clearRight()

            if (index != path[inputIndex]) {
                startError()
                return
            }

            inv.setItem(event.slot, green())
            lastGreenSlot = event.slot
            greenTicksRemaining = greenDuration

            inputIndex++

            if (inputIndex == step) {
                phase = Phase.SUCCESS
                ticks = 0
            }
        }

        override fun onHotbarButton(button: Int) {
            if (phase != Phase.INPUT) return

            if (button !in 0 until 9) return

            val slot = rightGrid[button]

            clearRight()

            if (button != path[inputIndex]) {
                startError()
                return
            }

            inv.setItem(slot, green())
            lastGreenSlot = slot
            greenTicksRemaining = greenDuration

            inputIndex++

            if (inputIndex == step) {
                phase = Phase.SUCCESS
                ticks = 0
            }
        }

        override fun tick() {
            if (path.isEmpty()) return
            ticks++

            when (phase) {

                Phase.SHOW -> {
                    if (ticks % SHOW_TICK != 0) return

                    clearLeft()

                    if (showIndex < step) {
                        if (blinkOn) {
                            inv.setItem(leftGrid[path[showIndex]], yellow())
                            showIndex++
                        }
                        blinkOn = !blinkOn
                    } else {
                        phase = Phase.INPUT
                        ticks = 0
                        showIndex = 0
                        blinkOn = false
                        clearLeft()
                    }
                }

                Phase.INPUT -> {
                    if (greenTicksRemaining > 0) {
                        greenTicksRemaining--
                        if (greenTicksRemaining == 0 && lastGreenSlot != -1) {
                            inv.setItem(lastGreenSlot, gray())
                            lastGreenSlot = -1
                        }
                    }
                }

                Phase.SUCCESS -> {
                    if (ticks == SUCCESS_TIME) {
                        if (step == path.size) {
                            player.game.taskManager.completeTask(this)
                        } else {
                            step++
                            resetForNextRound()
                        }
                    }
                }

                Phase.ERROR -> {
                    if (ticks % ERROR_BLINK == 0) {
                        val item = if (blinkOn) red() else gray()
                        (leftGrid + rightGrid).forEach { inv.setItem(it, item) }
                        blinkOn = !blinkOn
                    }
                    if (ticks >= ERROR_TIME) {
                        generateNewPath()
                        resetState()
                    }
                }

            }
        }

        override fun onInventoryClose() {
            startError()
        }

        /* ---------------- helpers ---------------- */

        private fun generateNewPath() {
            path = MutableList(PATH_LENGTH) { Random.nextInt(9) }
            step = 1
        }

        private fun resetState() {
            phase = Phase.SHOW
            ticks = 0
            showIndex = 0
            inputIndex = 0
            blinkOn = false
            greenTicksRemaining = 0
            lastGreenSlot = -1
            clearLeft()
            clearRight()
        }

        private fun resetForNextRound() {
            phase = Phase.SHOW
            ticks = 0
            showIndex = 0
            inputIndex = 0
            blinkOn = false
            greenTicksRemaining = 0
            lastGreenSlot = -1
            clearLeft()
            clearRight()
        }

        private fun startError() {
            phase = Phase.ERROR
            ticks = 0
            blinkOn = false
        }

        private fun drawBackground() {
            val bg = ItemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            for (i in 0 until SIZE) inv.setItem(i, bg)
        }

        private fun drawFrames() {
            val frame = ItemStack(Material.GRAY_STAINED_GLASS_PANE).hideTooltip()
            (leftGrid + rightGrid).forEach { slot ->
                if (slot in 0 until SIZE && inv.getItem(slot)?.type == Material.BLACK_STAINED_GLASS_PANE) {
                    inv.setItem(slot, frame)
                }
            }
        }

        private fun clearLeft() = leftGrid.forEach { inv.setItem(it, gray()) }
        private fun clearRight() = rightGrid.forEach { inv.setItem(it, gray()) }

        private fun gray() = ItemStack(Material.GRAY_STAINED_GLASS_PANE).hideTooltip()
        private fun yellow() = ItemStack(Material.YELLOW_STAINED_GLASS_PANE).hideTooltip()
        private fun green() = ItemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip()
        private fun red() = ItemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip()

        companion object {
            private const val SIZE = 45
            private const val PATH_LENGTH = 5

            private const val SHOW_TICK = 10
            private const val SUCCESS_TIME = 20

            private const val ERROR_TIME = 40
            private const val ERROR_BLINK = 5
        }

        private enum class Phase {
            SHOW, INPUT, SUCCESS, ERROR
        }
    }
}