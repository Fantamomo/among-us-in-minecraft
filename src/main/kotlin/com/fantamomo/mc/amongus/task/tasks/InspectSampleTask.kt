package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.task.areaLocation
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.hideTooltip
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionType
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

object InspectSampleTask :
    Task<InspectSampleTask, InspectSampleTask.AssignedInspectSampleTask> {

    override val id = "inspect_sample"
    override val type = TaskType.LONG

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedInspectSampleTask(player)

    class AssignedInspectSampleTask(
        override val player: AmongUsPlayer
    ) : GuiAssignedTask<InspectSampleTask, AssignedInspectSampleTask>() {

        override val task = InspectSampleTask
        override val location: Location =
            areaLocation ?: error("No location for task $id")

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, TITLE)

        private val borderSlots = buildList {
            addAll(getBorderItemSlots(SIZE))
            addAll(EXTRA_BORDER_SLOTS)
        }

        private val targetBottle = Random.nextInt(MAX_SAMPLES)

        private val timeSlots = buildList {
            addAll(0 until 9)
            add(17)
            add(26)
            addAll(35 downTo 27)
            add(18)
            add(9)
        }

        private val backgroundItem = item(Material.BLACK_STAINED_GLASS_PANE)
        private val greenItem = item(Material.LIME_STAINED_GLASS_PANE)
        private val grayItem = item(Material.GRAY_STAINED_GLASS_PANE)

        private val countdown = Cooldown(1.minutes)
        private var ticks = 0
        private var hopperIndex: Int? = null
        private var open = false
        /* ---------------- Inventory Events ---------------- */

        init {
            countdown.onFinish(::setupInventory)
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem ?: return
            if (!item.isMine()) return
            if (countdown.isFinished()) {
                if (item.isMarkedWith("target")) {
                    player.game.taskManager.completeTask(this)
                } else if (item.isMarkedWith("wrong")) {
                    resetState()
                    setupInventory()
                }
                return
            }
            if (countdown.isRunning()) return
            if (item.isMarkedWith("start")) {
                hopperIndex = 0
                inv.setItem(START_BUTTON_SLOT, backgroundItem)
            }
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            open = false
            if (!countdown.isRunning() && !countdown.isFinished()) resetState()
        }

        /* ---------------- Setup ---------------- */

        override fun setupInventory() {
            open = true
            borderSlots.forEach { inv.setItem(it, backgroundItem) }

            fillBottleRow()
            fillMiddleRow()

            inv.setItem(
                HOPPER_BASE_SLOT + hopperOffset(),
                hopperItem()
            )

            if (!countdown.isRunning() && !countdown.isFinished()) {
                inv.setItem(START_BUTTON_SLOT, buttonItem())
            }
            if (countdown.isFinished()) {
                for (bottle in 0 until MAX_SAMPLES) {
                    inv.setItem(BOTTLE_BASE_SLOT + bottle, if (bottle == targetBottle) targetBottle() else waterBottle())
                }
            }
        }

        /* ---------------- Tick Logic ---------------- */

        override fun tick() {
            if (!open) return

            if (countdown.isRunning()) {
                val start = countdown.startDuration().inWholeMilliseconds
                val remaining = countdown.remaining().inWholeMilliseconds
                val progress = 1f - (remaining / start.toFloat())
                val size = timeSlots.size
                for (i in 0 until size) {
                    val f = i / size.toFloat()
                    if (f <= progress) inv.setItem(timeSlots[i], greenItem)
                }
            }

            val index = hopperIndex ?: return
            ticks++

            when {
                ticks % 20 == 0 -> advanceHopper(index)
                ticks % 10 == 0 -> fillBottle(index)
            }
        }

        private fun advanceHopper(index: Int) {
            if (index >= MAX_SAMPLES) {
                countdown.start()
                hopperIndex = null
                return
            }

            clearHopperRow()
            inv.setItem(HOPPER_BASE_SLOT + index, hopperItem())
        }

        private fun fillBottle(index: Int) {
            inv.setItem(BOTTLE_BASE_SLOT + index, waterBottle())
            hopperIndex = index + 1
        }

        /* ---------------- Helpers ---------------- */

        private fun fillBottleRow() {
            val bottle =
                if (countdown.isRunning()) blueBottle()
                else emptyBottle()

            (BOTTLE_BASE_SLOT until BOTTLE_BASE_SLOT + MAX_SAMPLES)
                .forEach { inv.setItem(it, bottle) }
        }

        private fun fillMiddleRow() {
            (MIDDLE_ROW_START..MIDDLE_ROW_END)
                .forEach { inv.setItem(it, grayItem) }
        }

        private fun clearHopperRow() {
            (HOPPER_BASE_SLOT..HOPPER_ROW_END)
                .forEach { inv.setItem(it, grayItem) }
        }

        private fun hopperOffset() =
            if (countdown.isRunning() || countdown.isFinished()) 4 else 0

        private fun resetState() {
            hopperIndex = null
            ticks = 0
            countdown.reset()
        }

        /* ---------------- Item Factories ---------------- */

        private fun item(type: Material) =
            itemStack(type).hideTooltip()

        private fun hopperItem() =
            itemStack(Material.HOPPER).hideTooltip()

        private fun buttonItem() =
            itemStack(Material.OAK_BUTTON).hideTooltip().markWith("start")

        private fun emptyBottle() =
            itemStack(Material.GLASS_BOTTLE).hideTooltip()

        private fun blueBottle(): ItemStack =
            itemStack(Material.POTION).hideTooltip().apply {
                @Suppress("UnstableApiUsage")
                setData(
                    DataComponentTypes.POTION_CONTENTS,
                    PotionContents.potionContents().customColor(Color.BLUE)
                )
            }

        private fun waterBottle(): ItemStack =
            itemStack(Material.POTION).hideTooltip().markWith("wrong").apply {
                @Suppress("UnstableApiUsage")
                setData(
                    DataComponentTypes.POTION_CONTENTS,
                    PotionContents.potionContents().potion(PotionType.WATER)
                )
            }
        private fun targetBottle(): ItemStack = itemStack(Material.POTION).hideTooltip().markWith("target").apply {
            @Suppress("UnstableApiUsage")
            setData(
                DataComponentTypes.POTION_CONTENTS,
                PotionContents.potionContents().customColor(color)
            )
        }

        companion object {
            const val SIZE = 36
            val TITLE = Component.translatable("tasks.inspect_sample.title")

            const val START_BUTTON_SLOT = SIZE - 1

            const val BOTTLE_BASE_SLOT = 20
            const val HOPPER_BASE_SLOT = 11
            const val HOPPER_ROW_END = 15

            const val MIDDLE_ROW_START = 11
            const val MIDDLE_ROW_END = 15

            const val MAX_SAMPLES = 5

            val EXTRA_BORDER_SLOTS = setOf(10, 16, 19, 25)
            val color: Color = Color.RED
        }
    }
}