package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import com.fantamomo.mc.amongus.player.bot.nav.NavNode
import com.fantamomo.mc.amongus.util.randomWithWeights
import net.minecraft.world.entity.ai.goal.PanicGoal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class RunAwayFromBodyGoal(mob: AmongUsZombie, speedModifier: Double = 1.5) : PanicGoal(mob, speedModifier) {
    val player = mob.controller.player

    private var target: NavNode? = null

    override fun shouldPanic(): Boolean {
        val lastKillTime = player.lastKillTime ?: return false
        val current = Clock.System.now()
        return lastKillTime + 10.seconds > current
    }

    override fun canContinueToUse() = super.canContinueToUse() && Clock.System.now() < player.lastKillTime!! + 20.seconds

    override fun findRandomPosition(): Boolean {
        val graph = player.game.navGraph
        val nodes = graph.nodes
        if (nodes.isEmpty()) return false
        val candidates = mutableMapOf<NavNode, Double>()
        val currentPos = mob.blockPosition()
        for (node in nodes) {
            val pos = node.pos

            val distSqr = currentPos.distSqr(pos)
            if (distSqr > 16 * 16) {
                candidates.putIfAbsent(node, distSqr)
            }
        }
        if (candidates.isEmpty()) return false

        target = candidates.randomWithWeights()
        return true
    }

    override fun start() {
        player.controller.executionAbilityGoal = this
        player.controller.handle.navigation.moveToWithGraph(target!!.pos, speedModifier)
        isRunning = true
    }

    override fun stop() {
        super.stop()
        player.controller.executionAbilityGoal = null
    }
}