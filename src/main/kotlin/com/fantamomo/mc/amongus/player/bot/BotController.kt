package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.goals.MoveToTaskGoal
import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.player.Player
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Zombie

@NMS
class BotController(val player: BotAmongUsPlayer) {

    val handle: AmongUsZombie = AmongUsZombie(this, (player.game.world as CraftWorld).handle)
    val entity = handle.bukkitEntity as Zombie

    private var configuredPhase: GamePhase? = null

    init {
        handle.navigation.pathFinder.setMaxVisitedNodes(80 * 16)
        onPhaseChange()
    }

    fun onPhaseChange() {
        when (val phase = player.game.phase) {
            GamePhase.LOBBY,
            GamePhase.STARTING -> applyGoals(phase, ::addLobbyGoals)

            GamePhase.REVEALING_ROLES -> clearGoals()

            GamePhase.RUNNING -> applyGoals(phase, ::addRunningGoals)

            GamePhase.CALLING_MEETING,
            GamePhase.DISCUSSION,
            GamePhase.VOTING,
            GamePhase.ENDING_MEETING -> applyGoals(phase, ::addMeetingGoals)

            GamePhase.FINISHED -> clearGoals()
        }
    }

    private fun addLobbyGoals() {
        val gs = handle.goalSelector
        gs.addGoal(1, LookAtPlayerGoal(handle, Player::class.java, 8.0f))
        gs.addGoal(2, RandomLookAroundGoal(handle))
        gs.addGoal(3, RandomStrollGoal(handle, 1.0, 40, false))
    }

    private fun addRunningGoals() {
        val gs = handle.goalSelector
        gs.addGoal(1, MoveToTaskGoal(player, handle, 1.5))
        gs.addGoal(6, LookAtPlayerGoal(handle, Player::class.java, 6.0f))
        gs.addGoal(7, RandomLookAroundGoal(handle))
    }

    private fun addMeetingGoals() {
        val gs = handle.goalSelector
        gs.addGoal(1, RandomStrollGoal(handle, 1.0, 40, false))
        gs.addGoal(2, LookAtPlayerGoal(handle, Player::class.java, 12.0f, 1.0f))
        gs.addGoal(3, RandomLookAroundGoal(handle))
    }

    private fun applyGoals(phase: GamePhase, setup: () -> Unit) {
        if (configuredPhase == phase) return
        clearGoals()
        setup()
        configuredPhase = phase
    }

    private fun clearGoals() {
        handle.goalSelector.removeAllGoals { true }
        handle.targetSelector.removeAllGoals { true }
        handle.navigation.stop()
        configuredPhase = null
    }
}