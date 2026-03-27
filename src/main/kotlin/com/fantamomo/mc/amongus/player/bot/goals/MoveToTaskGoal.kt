package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.player.AmongUsMannequin
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.task.BotSupportingTask
import com.fantamomo.mc.amongus.task.MultiStepTask
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.ticks
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal
import net.minecraft.world.entity.decoration.Mannequin
import net.minecraft.world.level.LevelReader
import java.util.*
import kotlin.time.Duration.Companion.seconds

class MoveToTaskGoal(
    private val player: BotAmongUsPlayer,
    mob: PathfinderMob,
    speedModifier: Double = 1.5,
) : MoveToBlockGoal(mob, speedModifier, 0, 0), CustomGoalDebugName {

    companion object {
        private const val DEFAULT_COMPLETE_DELAY_TICKS = 100
        private const val DEFAULT_COOLDOWN_TICKS_MIN = 50L
        private const val PATH_RECALCULATION_DELAY = 40L

        private val defaultDurationProvider = BotSupportingTask.BotTaskDuration.range(5.seconds, 8.seconds)
    }

    enum class State {
        NAVIGATING,
        WORKING,
        IDLE,
        COOLDOWN
    }

    var targetTask: TaskManager.RegisteredTask? = null
        private set
    private var targetTaskSet: Long = -1L
    var completeDelayTicks: Int = DEFAULT_COMPLETE_DELAY_TICKS
        private set
    var workingTicks = 0
        private set
    private var cooldownTicksEnd = -1L
    var state = State.IDLE
        private set
    private var recalculatePathIn = 0L

    init {
        setFlags(EnumSet.of(Flag.MOVE))
    }

    override fun canUse(): Boolean {
        if (state == State.COOLDOWN && cooldownTicksEnd != -1L && cooldownTicksEnd > GameManager.currentTick.ticks) return false
        if (player.tasks.all { it.completed }) return false
        return findNearestBlock()
    }

    override fun canContinueToUse(): Boolean = when (state) {
        State.NAVIGATING -> tryTicks in 0..500 && !mob.navigation.isStuck && isValidTarget(mob.level(), blockPos)
        State.WORKING -> workingTicks < completeDelayTicks
        State.IDLE, State.COOLDOWN -> false
    }

    override fun start() {
        super.start()
        workingTicks = 0
        cooldownTicksEnd = -1L
        state = State.NAVIGATING
        mob.movingTarget = blockPos
    }

    override fun stop() {
        super.stop()
        targetTask = null
        recalculatePathIn = -1L
        targetTaskSet = -1L
        completeDelayTicks = DEFAULT_COMPLETE_DELAY_TICKS
        workingTicks = 0
        if (state != State.COOLDOWN) {
            state = State.IDLE
        }
    }

    override fun tick() {
        super.tick()
        when (state) {
            State.NAVIGATING -> {
                if (isReachedTarget) {
                    state = State.WORKING
                    workingTicks = 0
                    mob.navigation.stop()
                }
            }

            State.WORKING -> {
                workingTicks++
                if (workingTicks >= completeDelayTicks) {
                    completeCurrentTask()
                }
            }

            State.IDLE, State.COOLDOWN -> {}
        }
    }

    override fun shouldRecalculatePath(): Boolean {
        if (targetTask != null && targetTaskSet != -1L && GameManager.currentTick.ticks - targetTaskSet < PATH_RECALCULATION_DELAY) return false
        recalculatePathIn++
        return recalculatePathIn % 40 == 0L || super.shouldRecalculatePath()
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
        targetTaskSet = GameManager.currentTick.ticks
        completeDelayTicks =
            (closest.task as? BotSupportingTask)?.getTaskDurationForBot()?.getDuration(player)?.ticks?.toInt()
                ?: defaultDurationProvider.getDuration(player).ticks.toInt()
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

        val mannequins = mob.level().getEntitiesOfClass(
            Mannequin::class.java,
            this.mob.boundingBox.inflate(20.0),
            ::checkTarget
        )

        if (mannequins.isNotEmpty()) {
            state = State.COOLDOWN
            cooldownTicksEnd =
                GameManager.currentTick.ticks + DEFAULT_COOLDOWN_TICKS_MIN
        } else {
            state = State.IDLE
            cooldownTicksEnd = -1
        }
    }

    private fun checkTarget(target: Mannequin): Boolean {
        if (target !is AmongUsMannequin) return false
        val owner = target.controller.owner
        return player.canSee(owner) && owner.mannequinController.handle?.hasLineOfSight(mob) == true
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