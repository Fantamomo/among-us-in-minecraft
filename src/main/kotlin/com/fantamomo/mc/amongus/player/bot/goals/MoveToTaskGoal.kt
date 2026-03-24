package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.task.BotSupportingTask
import com.fantamomo.mc.amongus.task.MultiStepTask
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.ticks
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal
import net.minecraft.world.level.LevelReader
import org.bukkit.craftbukkit.entity.CraftEntity
import kotlin.time.Duration.Companion.seconds

class MoveToTaskGoal(
    private val player: BotAmongUsPlayer,
    mob: PathfinderMob,
    speedModifier: Double = 1.5,
) : MoveToBlockGoal(mob, speedModifier, 0, 0), CustomGoalDebugName {

    companion object {
        private const val DEFAULT_COMPLETE_DELAY_TICKS = 100

        private val defaultDurationProvider = BotSupportingTask.BotTaskDuration.range(5.seconds, 8.seconds)
    }

    private enum class State {
        NAVIGATING,
        WORKING,
        IDLE
    }

    private var targetTask: TaskManager.RegisteredTask? = null
    private var completeDelayTicks: Int = DEFAULT_COMPLETE_DELAY_TICKS
    private var state = State.IDLE
    private var workingTicks = 0

    override fun canUse(): Boolean {
        if (player.tasks.none { !it.completed }) return false
        return findNearestBlock()
    }

    override fun canContinueToUse(): Boolean = when (state) {
        State.NAVIGATING -> tryTicks in 0..500 && !mob.navigation.isStuck && isValidTarget(mob.level(), blockPos)
        State.WORKING -> workingTicks < completeDelayTicks
        State.IDLE -> false
    }

    override fun start() {
        super.start()
        workingTicks = 0
        state = State.NAVIGATING
        mob.movingTarget = blockPos
    }

    override fun stop() {
        super.stop()
        targetTask = null
        completeDelayTicks = DEFAULT_COMPLETE_DELAY_TICKS
        workingTicks = 0
        state = State.IDLE
    }

    override fun tick() {
        super.tick()
        when (state) {
            State.NAVIGATING -> {
                if (isReachedTarget) {
                    state = State.WORKING
                    workingTicks = 0
                    mob.navigation.stop()
                    mob.lookAt(
                        (targetTask?.display as CraftEntity).handle,
                        30f, 30f
                    )
                }
            }

            State.WORKING -> {
                workingTicks++
                if (workingTicks >= completeDelayTicks) {
                    completeCurrentTask()
                }
            }

            State.IDLE -> {}
        }
    }

    override fun findNearestBlock(): Boolean {
        val pending = player.tasks.filter { !it.completed }
        if (pending.isEmpty()) return false

        val mobPos = mob.blockPosition()

        val closest = pending.minByOrNull { registered ->
            val pos = registered.task.location.run { BlockPos(blockX, blockY - 1, blockZ) }
            val path = mob.navigation.createPath(pos, 0)
            path?.distToTarget?.toDouble() ?: mobPos.distSqr(pos)
        } ?: return false

        targetTask = closest
        completeDelayTicks =
            (closest.task as? BotSupportingTask)?.getTaskDurationForBot()?.getDuration(player)?.ticks?.toInt() ?: defaultDurationProvider.getDuration(player).ticks.toInt()
        blockPos = closest.task.location.run { BlockPos(blockX, blockY - 1, blockZ) }
        mob.movingTarget = blockPos
        return true
    }

    private fun completeCurrentTask() {
        val registeredTask = targetTask ?: return
        val task = registeredTask.task
        val taskManager = player.game.taskManager
        if (task is MultiStepTask) taskManager.completeOneTaskStep(task)
        else taskManager.completeTask(task)
        state = State.IDLE
    }

    override fun nextStartTick(creature: PathfinderMob) = 5
    override fun getMoveToTarget() = blockPos
    override fun acceptedDistance() = 2.0

    override fun isValidTarget(level: LevelReader, pos: BlockPos): Boolean {
        val task = targetTask ?: return false
        if (task.completed) return false
        return task.task.location.run { pos.x == blockX && pos.y == blockY && pos.z == blockZ }
    }

    override fun getDebugName() = this::class.java.simpleName + targetTask?.task?.task?.id?.let { ": $it" }.orEmpty()
}