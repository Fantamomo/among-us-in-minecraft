package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.style
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.*
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.Style
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import kotlin.time.Duration.Companion.minutes

object RebootWifi : Task<RebootWifi, RebootWifi.AssignedRebootWifi> {
    override val id: String = "reboot_wifi"
    override val type: TaskType = TaskType.LONG

    override fun isAvailable(game: Game): Boolean = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer): AssignedRebootWifi = AssignedRebootWifi(player)

    class AssignedRebootWifi(override val player: AmongUsPlayer) : GuiAssignedTask<RebootWifi, AssignedRebootWifi>() {
        override val task: RebootWifi = RebootWifi
        override val inv: Inventory = Bukkit.createInventory(this, SIZE, Component.translatable(task.title))
        override val location: Location = areaLocation ?: error("No location for task $id")

        private val countdown = Cooldown(1.minutes)
        private var open = false

        private val timeSlots = buildList {
            addAll(0 until 9)
            add(17)
            addAll(26 downTo 18)
            add(9)
        }

        private val backgroundItem = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
        private val greenItem = itemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip()

        init {
            countdown.onFinish {
                if (open) setupInventory()
            }
        }

        override fun setupInventory() {
            open = true
            (0 until SIZE).forEach { inv.setItem(it, backgroundItem) }

            if (countdown.isFinished()) {
                inv.setItem(REBOOT_SLOT, itemStack(Material.LIME_STAINED_GLASS_PANE).hideTooltip().markWith("complete"))
            } else if (!countdown.isRunning()) {
                val rebootButton = itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip().markWith("reboot_button")
                inv.setItem(REBOOT_SLOT, rebootButton)
            } else {
                inv.setItem(REBOOT_SLOT, backgroundItem)
            }
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem?.takeIf { it.isMine() } ?: return
            if (item.isMarkedWith("reboot_button") && !countdown.isRunning() && !countdown.isFinished()) {
                countdown.start()
                setupInventory()
            } else if (item.isMarkedWith("complete") && countdown.isFinished()) {
                player.game.taskManager.completeTask(this)
            }
        }

        override fun onInventoryClose() {
            super.onInventoryClose()
            open = false
        }

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
        }

        override fun scoreboardLine(): Component = textComponent {
            translatable("tasks.$id.scoreboard") {
                args {
                    numeric("remaining_time", countdown.remaining().inWholeSeconds)
                }
            }
        }

        override fun scoreboardLine(style: Style): Component = textComponent {
            translatable("tasks.$id.scoreboard") {
                args {
                    numeric("remaining_time", countdown.remaining().inWholeSeconds)
                }
                style(style)
            }
        }

        override fun state(): TaskState? =
            TaskState.IN_PROGRESS.takeIf { countdown.isRunning() || countdown.isFinished() }

        companion object {
            const val SIZE = 27
            const val REBOOT_SLOT = 13
        }
    }
}