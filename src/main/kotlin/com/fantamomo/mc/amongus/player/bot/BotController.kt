package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.goals.LookAtPlayerGoal
import com.fantamomo.mc.amongus.player.bot.goals.LookAtTaskGoal
import com.fantamomo.mc.amongus.player.bot.goals.MoveToTaskGoal
import com.fantamomo.mc.amongus.player.bot.goals.RandomMoveGoal
import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Zombie
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.random.Random

@NMS
class BotController(val player: BotAmongUsPlayer) {

    val handle: AmongUsZombie = AmongUsZombie(this, (player.game.world as CraftWorld).handle)
    val entity = handle.bukkitEntity as Zombie

    private var configuredPhase: GamePhase? = null

    var moveToTaskGoal: MoveToTaskGoal? = null
        private set

    init {
        handle.navigation.pathFinder.setMaxVisitedNodes(80 * 16)
        onPhaseChange()
    }

    fun onPhaseChange() {
        val phase = player.game.phase
        if (configuredPhase == phase) return
        when (phase) {
            GamePhase.LOBBY,
            GamePhase.STARTING -> {
                if (configuredPhase != GamePhase.LOBBY && configuredPhase != GamePhase.STARTING) {
                    applyGoals(::addLobbyGoals)
                }
            }

            GamePhase.REVEALING_ROLES -> clearGoals()

            GamePhase.RUNNING -> {
                clearGoals()
                AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
                    if (configuredPhase != GamePhase.RUNNING) return@runTaskLater
                    applyGoals(::addRunningGoals)
                }, 20L + Random.nextLong(50L))
            }

            GamePhase.CALLING_MEETING,
            GamePhase.DISCUSSION,
            GamePhase.VOTING -> {
                if (configuredPhase != GamePhase.CALLING_MEETING && configuredPhase != GamePhase.DISCUSSION && configuredPhase != GamePhase.VOTING) {
                    applyGoals(::addMeetingGoals)
                }
            }

            GamePhase.ENDING_MEETING -> clearGoals()

            GamePhase.FINISHED -> clearGoals()
        }
        configuredPhase = phase
    }

    private fun addLobbyGoals() {
        val gs = handle.goalSelector
        gs.addGoal(1, LookAtPlayerGoal(handle, 8.0f))
        gs.addGoal(2, RandomLookAroundGoal(handle))
        gs.addGoal(3, RandomStrollGoal(handle, 1.0, 40, false))
    }

    private fun addRunningGoals() {
        val gs = handle.goalSelector
        gs.addGoal(1, MoveToTaskGoal(player, handle, 1.5).also { moveToTaskGoal = it })
        gs.addGoal(2, LookAtTaskGoal(handle))
        gs.addGoal(6, LookAtPlayerGoal(handle, 6.0f))
        gs.addGoal(7, RandomLookAroundGoal(handle))
        gs.addGoal(7, RandomMoveGoal(handle, 1.0, 40, 20))
    }

    private fun addMeetingGoals() {
        val gs = handle.goalSelector
        gs.addGoal(1, RandomStrollGoal(handle, 1.0, 10, false))
        gs.addGoal(2, LookAtPlayerGoal(handle, 12.0f, 1.0f))
        gs.addGoal(3, RandomLookAroundGoal(handle))
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun applyGoals(setup: () -> Unit) {
        contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
        clearGoals()
        setup()
    }

    private fun clearGoals() {
        handle.goalSelector.removeAllGoals { true }
        handle.targetSelector.removeAllGoals { true }
        handle.target = null
        handle.goalSelector
        handle.navigation.stop()
        handle.moveControl.setWait()
        moveToTaskGoal = null
    }
}