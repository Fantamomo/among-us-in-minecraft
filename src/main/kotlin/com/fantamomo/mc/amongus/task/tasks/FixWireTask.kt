package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Steppable
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.task.tasks.FixWireTask.AssignedFixWireTask.Companion.STEPS
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object FixWireTask : Task<FixWireTask, FixWireTask.AssignedFixWireTask> {

    override val id = "fix_wire"
    override val type = TaskType.LONG

    override fun isAvailable(game: Game): Boolean {
        val locations = game.area.tasks[id]
        if (locations.isNullOrEmpty()) return false
        if (locations.size < STEPS) return false
        return true
    }

    override fun assignTo(player: AmongUsPlayer) = AssignedFixWireTask(player)

    class AssignedFixWireTask(
        override val player: AmongUsPlayer,
    ) : GuiAssignedTask<FixWireTask, AssignedFixWireTask>(), Steppable {
        private val locations =
            player.game.area.tasks[id]?.toMutableSet() ?: throw IllegalArgumentException("No locations for task $id")
        override var location: Location = locations.random().also { locations.remove(it) }

        override val task = FixWireTask

        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.fix_wire.title"))


        private val bg = Color(Material.LIGHT_GRAY_STAINED_GLASS_PANE)

        private var rowTargets = colors.shuffled().take(ROWS)
        private val editableSlots = mutableSetOf<Int>()

        override var step = 0

        override val maxSteps: Int = STEPS

        override fun setupInventory() {
            generateGrid()
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val slot = event.slot
            if (slot !in editableSlots) return

            inv.setItem(slot, rowTargets[slot / 9].item)

            checkComplete()
        }

        /* -------- generation -------- */

        private fun generateGrid() {
            repeat(SIZE) { inv.setItem(it, null) }

            for (row in 0 until ROWS) {
                val baseSlot = row * 9
                val targetColor = rowTargets[row]

                val holes = Random.nextInt(3, 8)
                val holeSlots =
                    (if (holes == 7) 1 until 8 else (0 until 9).shuffled().take(holes)).map { baseSlot + it }

                for (col in 0 until 9) {
                    val slot = baseSlot + col
                    if (slot in holeSlots) {
                        editableSlots += slot
                        inv.setItem(slot, bg.item)
                    } else {
                        inv.setItem(slot, targetColor.item)
                    }
                }
            }
        }

        /* -------- completion -------- */

        private fun checkComplete() {
            for (row in 0 until ROWS) {
                val target = rowTargets[row]
                for (col in 0 until 9) {
                    val slot = row * 9 + col
                    if (slot in editableSlots) {
                        val item = inv.getItem(slot) ?: return
                        if (target.material != item.type) return
                    }
                }
            }
            location = locations.random().also { locations.remove(it) }
            player.game.taskManager.completeOneTaskStep(this)
            step++
            stop()
        }

        override fun onInventoryClose() {
            editableSlots.clear()
            rowTargets = colors.shuffled().take(ROWS)
        }

        private data class Color(
            val material: Material
        ) {
            val item: ItemStack = ItemStack(material).hideTooltip()
        }

        companion object {
            const val SIZE = 54
            const val ROWS = 6
            const val STEPS = 3

            private val colors = listOf(
                Color(Material.RED_STAINED_GLASS_PANE),
                Color(Material.BLUE_STAINED_GLASS_PANE),
                Color(Material.GREEN_STAINED_GLASS_PANE),
                Color(Material.YELLOW_STAINED_GLASS_PANE),
                Color(Material.ORANGE_STAINED_GLASS_PANE),
                Color(Material.PURPLE_STAINED_GLASS_PANE)
            )
        }
    }
}