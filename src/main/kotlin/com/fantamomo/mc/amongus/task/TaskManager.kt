package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.GuiAssignedTask.Companion.MOVEABLE_ITEM_KEY
import com.fantamomo.mc.amongus.util.data.TaskBarUpdateEnum
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import com.fantamomo.mc.amongus.util.randomListDistinct
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

class TaskManager(val game: Game) {
    private val tasks: MutableMap<AmongUsPlayer, MutableSet<RegisteredTask>> = mutableMapOf()
    private var commsSabotage: Boolean = game.sabotageManager.isSabotage(SabotageType.Communications)
    private val bossbar = BossBar.bossBar(
        Component.translatable("task.bossbar.title"),
        0f,
        BossBar.Color.GREEN,
        BossBar.Overlay.NOTCHED_20
    )

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

    internal fun updateBossbar(taskCompleted: Boolean = false, meeting: Boolean = false, force: Boolean = false) {
        val updateFrequency = game.settings[SettingsKey.TASK_BAR_UPDATE]
        if (updateFrequency == TaskBarUpdateEnum.NONE) return
        if (game.sabotageManager.isSabotage(SabotageType.Communications)) {
            bossbar.progress(0f)
            return
        }
        if (force || (taskCompleted && updateFrequency != TaskBarUpdateEnum.MEETING) || meeting) {
            val completedTasks =
                tasks.values.sumOf { tasks -> tasks.sumOf { if (it.completed && !it.fake) it.weight else 0 } }
            val totalTasks = tasks.values.sumOf { it.sumOf { task -> if (task.fake) 0 else task.weight } }
            bossbar.progress(if (totalTasks == 0) 0f else completedTasks.toFloat() / totalTasks)
        }
    }

    private fun get(task: AssignedTask<*, *>) = tasks[task.player]?.find { it.task == task }

    fun completeTask(task: AssignedTask<*, *>, modifyStatistics: Boolean = true) {
        val registeredTask = get(task) ?: return
        if (registeredTask.completed) return
        val allTasksCompleted = tasks[task.player]?.all { it.completed } ?: true
        registeredTask.completed = true
        registeredTask.hideCompletely()
        registeredTask.task.stop()
        updateBossbar(taskCompleted = true)
        task.player.player?.apply {
            sendTitlePart(TitlePart.TITLE, textComponent {
                translatable("task.complete.title")
            })
        }
        removeMoveableItems(task.player)
        AmongUs.server.scheduler.runTask(AmongUs) { ->
            removeMoveableItems(task.player)
        }
        game.scoreboardManager.refresh(task.player)
        if (modifyStatistics) {
            val statistics = task.player.statistics
            statistics.tasksCompleted.increment()
            val playerHasCompletedAllTasks = tasks[task.player]?.all { it.completed } ?: false
            if (!allTasksCompleted && playerHasCompletedAllTasks) statistics.fullyCompleteTasks.increment()
        }
        if (allTaskCompleted()) game.checkWin()
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
        removeMoveableItems(task.player)
        game.scoreboardManager.refresh(task.player)
        AmongUs.server.scheduler.runTask(AmongUs) { ->
            removeMoveableItems(task.player)
            game.scoreboardManager.refresh(task.player)
        }
    }

    private fun removeMoveableItems(player: AmongUsPlayer) {
        player.player?.run {
            inventory.forEachIndexed { index, stack ->
                if (stack?.persistentDataContainer?.has(
                        MOVEABLE_ITEM_KEY,
                        PersistentDataType.BYTE
                    ) == true
                ) inventory.setItem(index, null)
            }
            if (itemOnCursor.persistentDataContainer.has(MOVEABLE_ITEM_KEY, PersistentDataType.BYTE)) {
                setItemOnCursor(null)
            }
        }
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
            registeredTasks.find { !it.completed && it.task.location.isSameBlockPosition(location) }
                ?: return false
        if (task.fake) return true
        task.task.start()
        return true
    }

    fun get(task: AmongUsPlayer): MutableSet<RegisteredTask> = tasks[task] ?: mutableSetOf()

    fun assignTask(player: AmongUsPlayer, task: AssignedTask<*, *>) {
        val registeredTask = RegisteredTask(task, fake = !player.canDoTasks)
        tasks.getOrPut(player) { mutableSetOf() }.add(registeredTask)
        updateBossbar(true)
    }

    fun assignTask(player: AmongUsPlayer, task: Task<*, *>) {
        if (!task.isAvailable(game)) return
        val assignedTask = task.assignTo(player)
        assignTask(player, assignedTask)
    }

    fun assignTasks(player: AmongUsPlayer, tasks: Collection<Task<*, *>>) {
        tasks.forEach { assignTask(player, it) }
    }

    fun unassignTask(player: AmongUsPlayer, task: Task<*, *>) {
        tasks[player]?.removeIf { t ->
            if (t.task.task != task) return@removeIf false
            t.hideCompletely()
            true
        }
        updateBossbar(true)
    }

    fun removePlayer(player: AmongUsPlayer) {
        tasks.remove(player)
    }

    fun allTaskCompleted(): Boolean = tasks.values.all { tasks -> tasks.all { it.completed || it.fake } }

    fun start() {
        val longTasksCount = game.settings[SettingsKey.TASK_LONG]
        val shortTasksCount = game.settings[SettingsKey.TASK_SHORT]
        val commonTasksCount = game.settings[SettingsKey.TASK_COMMON]

        val tasks = Task.tasks.filter { it.isAvailable(game) }
        val longTasks = tasks.filter { it.type == TaskType.LONG }
        val shortTasks = tasks.filter { it.type == TaskType.SHORT }
        val commonTasks = tasks.filter { it.type == TaskType.COMMON }

        val showBossbar = game.settings[SettingsKey.TASK_BAR_UPDATE] != TaskBarUpdateEnum.NONE

        val common = commonTasks.randomListDistinct(commonTasksCount)

        for (player in game.players) {
            if (showBossbar) player.player?.showBossBar(bossbar)
            val long = longTasks.randomListDistinct(longTasksCount)
            val short = shortTasks.randomListDistinct(shortTasksCount)
            assignTasks(player, long + short + common)
        }
    }

    internal fun end() {
        for (viewer in bossbar.viewers().toList()) {
            viewer as? Player ?: continue
            viewer.hideBossBar(bossbar)
        }
        tasks.clear()
    }

    inner class RegisteredTask(
        val task: AssignedTask<*, *>,
        val fake: Boolean = false
    ) {
        val weight: Int = task.task.type.weight
        var completed: Boolean = false
        var isShown: Boolean = false
        val started: Boolean
            get() = completed || (task as? Steppable)?.step.let { it != null && it > 0 }

        val display: BlockDisplay = task.location.world.spawn(task.location, BlockDisplay::class.java) { display ->
            display.isVisibleByDefault = false
            display.block = task.location.block.blockData
            display.isGlowing = true
            display.glowColorOverride = Color.YELLOW
        }.also { EntityManager.addEntityToRemoveOnEnd(game, it) }

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

        fun state(): TaskState {
            val provided = task.state()?.takeUnless { it == TaskState.COMMUNICATIONS_SABOTAGED }
            if (provided == TaskState.COMPLETED) return provided
            val evaluatedState = when {
                completed -> TaskState.COMPLETED
                started -> TaskState.IN_PROGRESS
                else -> TaskState.INCOMPLETE
            }
            if (provided == null) return evaluatedState
            if (evaluatedState >= provided) return evaluatedState
            return provided
        }
    }
}