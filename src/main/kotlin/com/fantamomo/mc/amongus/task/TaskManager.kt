package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import net.kyori.adventure.text.minimessage.translation.Argument.numeric
import net.kyori.adventure.title.TitlePart
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay

class TaskManager(val game: Game) {
    private val tasks: MutableMap<AmongUsPlayer, MutableSet<RegisteredTask>> = mutableMapOf()
    private var commsSabotage: Boolean = game.sabotageManager.isSabotage(SabotageType.Communications)

    fun tick() {
        val sabotage = game.sabotageManager.isSabotage(SabotageType.Communications)
        for (player in tasks.keys) {
            val location = player.player?.location ?: continue
            for (registeredTask in tasks[player]!!) {
                if (!registeredTask.completed) {
                    registeredTask.task.tick()
                    if (sabotage != commsSabotage) {
                        registeredTask.waypointVisible(sabotage)
                    }
                }
                if (!registeredTask.completed && registeredTask.task.location.distanceSquared(location) <= 8 * 8) {
                    registeredTask.show()
                } else {
                    registeredTask.hide()
                }
            }
        }
        commsSabotage = sabotage
    }

    private fun get(task: AssignedTask<*, *>) = tasks[task.player]?.find { it.task == task }

    fun completeTask(task: AssignedTask<*, *>) {
        val registeredTask = get(task) ?: return
        if (registeredTask.completed) return
        registeredTask.completed = true
        registeredTask.hideCompletely()
        registeredTask.task.stop()
        task.player.player?.apply {
            sendTitlePart(TitlePart.TITLE, textComponent {
                translatable("task.complete.title")
            })
        }
    }

    fun <T> completeOneTaskStep(task: T) where T : Steppable, T : AssignedTask<*, *> {
        if (task.step + 1 >= task.maxSteps) {
            completeTask(task)
            return
        }
        task.player.player?.sendTitlePart(TitlePart.TITLE, textComponent {
            translatable("task.step.title") {
                args {
                    numeric("step", task.step + 1)
                    numeric("total", task.maxSteps)
                }
            }
        })
        updateTask(task)
    }

    fun abortTask(task: AssignedTask<*, *>) {
        task.stop()
    }

    fun updateTask(task: AssignedTask<*, *>) {
        get(task)?.update()
    }

    fun startTask(player: AmongUsPlayer, location: Location): Boolean {
        val registeredTasks = tasks[player] ?: return false
        val task =
            registeredTasks.find { !it.completed && it.task.location.isSameBlockPosition(location) } ?: return false
        task.task.start()
        return true
    }

    fun get(task: AmongUsPlayer): MutableSet<RegisteredTask> = tasks[task] ?: mutableSetOf()

    fun assignTask(player: AmongUsPlayer, task: AssignedTask<*, *>) {
        val registeredTask = RegisteredTask(task)
        tasks.getOrPut(player) { mutableSetOf() }.add(registeredTask)
    }

    fun assignTask(player: AmongUsPlayer, task: Task<*, *>) {
        val assignedTask = task.assignTo(player) ?: return
        assignTask(player, assignedTask)
    }

    inner class RegisteredTask(
        val task: AssignedTask<*, *>
    ) {
        var completed: Boolean = false
        var isShown: Boolean = false

        val display: BlockDisplay = task.location.world.spawn(task.location, BlockDisplay::class.java) { display ->
            display.isVisibleByDefault = false
            display.block = task.location.block.blockData
            display.isGlowing = true
            display.glowColorOverride = Color.YELLOW
            EntityManager.addEntityToRemoveOnStop(display)
        }

        val waypoint: WaypointManager.Waypoint =
            WaypointManager.Waypoint("tasks.${task.task.id}.waypoint", Color.YELLOW, task.location)

        init {
            game.waypointManager.assignWaypoint(task.player, waypoint)
        }

        fun update() {
            display.teleport(task.location)
            display.block = task.location.block.blockData
            waypoint.setLocation(task.location)
        }

        fun hide() {
            if (!isShown) return
            task.player.player?.hideEntity(AmongUs, display)
            isShown = false
        }

        fun hideCompletely() {
            task.player.player?.hideEntity(AmongUs, display)
            game.waypointManager.removeWaypoint(task.player, waypoint)
        }

        fun show() {
            if (isShown) return
            task.player.player?.showEntity(AmongUs, display)
            isShown = true
        }

        fun waypointVisible(sabotage: Boolean) {
            waypoint.isVisible = !sabotage
        }
    }
}