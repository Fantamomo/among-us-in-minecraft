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

object VerifyIdTask : Task<VerifyIdTask, VerifyIdTask.AssignedVerifyIdTask> {

    override val id = "verify_id"
    override val type = TaskType.COMMON

    override fun isAvailable(game: Game) =
        !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) =
        AssignedVerifyIdTask(player)

    class AssignedVerifyIdTask(
        override val player: AmongUsPlayer
    ) : GuiAssignedTask<VerifyIdTask, AssignedVerifyIdTask>() {

        override val task = VerifyIdTask

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.verify_id.title"))

        override val location: Location =
            areaLocation ?: error("No location for task $id")

        private var code = List(7) { 1 }
        private var inputIndex = 0

        private var phase = Phase.INPUT
        private var ticks = 0

        private var greenTicksRemaining = 0
        private var lastGreenSlot = -1

        override fun setupInventory() {
            drawBackground()
            generateCode()
            drawCodeRow()
            drawNumberGrid()
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            if (phase != Phase.INPUT) return
            if (event.slot !in gridSlots) return

            val index = gridSlots.indexOf(event.slot)
            handleInput(index)
        }

        override fun onHotbarButton(button: Int) {
            if (phase != Phase.INPUT) return
            if (button !in 0..8) return

            handleInput(button)
        }

        override fun tick() {
            ticks++

            if (greenTicksRemaining > 0) {
                greenTicksRemaining--
                if (greenTicksRemaining == 0 && lastGreenSlot != -1) {
                    inv.setItem(lastGreenSlot, gray(gridSlots.indexOf(lastGreenSlot) + 1))
                    lastGreenSlot = -1
                }
            }

            if (phase == Phase.ERROR) {
                if (ticks % (ERROR_BLINK * 2) == 0) {
                    gridSlots.forEachIndexed { index, i -> inv.setItem(i, red(index + 1)) }
                } else if (ticks % ERROR_BLINK == 0) {
                    gridSlots.forEachIndexed { index, i -> inv.setItem(i, gray(index + 1)) }
                }
                if (ticks >= ERROR_TIME) {
                    reset()
                }
            }
        }

        override fun onInventoryClose() {
            reset()
            super.onInventoryClose()
        }

        private fun handleInput(index: Int) {
            val number = index + 1
            val slot = gridSlots[index]

            drawNumberGrid()

            if (number != code[inputIndex]) {
                startError()
                return
            }

            inv.setItem(slot, green(number))
            inv.setItem(codeSlots[inputIndex], green(code[inputIndex]))

            lastGreenSlot = slot
            greenTicksRemaining = GREEN_TIME

            inputIndex++

            if (inputIndex == code.size) {
                player.game.taskManager.completeTask(this)
            }
        }

        private fun generateCode() {
            code = List(7) { Random.nextInt(1, 10) }
            inputIndex = 0
        }

        private fun reset() {
            generateCode()
            drawCodeRow()
            drawNumberGrid()
            phase = Phase.INPUT
            ticks = 0
        }

        private fun startError() {
            phase = Phase.ERROR
            ticks = 0
            val red = red()
            gridSlots.forEach { inv.setItem(it, red) }
        }

        private fun drawBackground() {
            val bg = ItemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            for (i in 0 until SIZE) inv.setItem(i, bg)
        }

        private fun drawCodeRow() {
            codeSlots.forEachIndexed { index, slot ->
                inv.setItem(
                    slot,
                    blue(code[index])
                )
            }
        }

        private fun drawNumberGrid() {
            gridSlots.forEachIndexed { index, slot ->
                inv.setItem(
                    slot,
                    gray(index + 1)
                )
            }
        }

        private fun gray(amount: Int = 1) =
            itemStack(Material.GRAY_STAINED_GLASS_PANE, amount).hideTooltip()

        private fun blue(amount: Int) =
            itemStack(Material.BLUE_STAINED_GLASS_PANE, amount).hideTooltip()

        private fun green(amount: Int = 1) =
            itemStack(Material.LIME_STAINED_GLASS_PANE, amount).hideTooltip()

        private fun red(amount: Int = 1) =
            itemStack(Material.RED_STAINED_GLASS_PANE, amount).hideTooltip()

        companion object {
            private const val SIZE = 54
            private const val GREEN_TIME = 10
            private const val ERROR_TIME = 40
            private const val ERROR_BLINK = 5

            private val codeSlots = listOf(1, 2, 3, 4, 5, 6, 7)

            private val gridSlots = listOf(
                21, 22, 23,
                30, 31, 32,
                39, 40, 41
            )
        }

        private enum class Phase {
            INPUT, ERROR
        }
    }
}