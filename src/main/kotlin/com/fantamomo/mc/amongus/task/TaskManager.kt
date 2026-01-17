package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay

class TaskManager(val game: Game) {
    private val tasks: MutableMap<AmongUsPlayer, MutableSet<RegisteredTask>> = mutableMapOf()

    fun tick() {
        for (player in tasks.keys) {
            val location = player.player?.location ?: continue
            for (registeredTask in tasks[player]!!) {
                if (!registeredTask.completed && registeredTask.task.location.distanceSquared(location) <= 8 * 8) {
                    registeredTask.show()
                } else {
                    registeredTask.hide()
                }
            }
        }
    }

    private fun get(task: AssignedTask<*, *>) = tasks[task.player]?.find { it.task == task }

    fun completeTask(task: AssignedTask<*, *>) {
        val registeredTask = get(task) ?: return
        registeredTask.completed = true
        registeredTask.hideCompletely()
        registeredTask.task.stop()
    }

    fun completeOneTaskStep(task: AssignedTask<*, *>) {
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
        val task = registeredTasks.find { !it.completed && it.task.location.isSameBlockPosition(location) } ?: return false
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

        val waypoint: WaypointManager.Waypoint = WaypointManager.Waypoint("task.waypoint.${task.task.id}", Color.YELLOW, task.location)

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
    }
}