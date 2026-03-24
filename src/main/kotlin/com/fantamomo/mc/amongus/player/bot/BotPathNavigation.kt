package com.fantamomo.mc.amongus.player.bot

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.PathFinder

class BotPathNavigation(
    mob: AmongUsZombie,
    level: Level,
) : GroundPathNavigation(mob, level) {

    private val auZombie get() = mob as AmongUsZombie
    private val botPlayer get() = auZombie.controller.player
    private lateinit var botEvaluator: BotNodeEvaluator

    private var ventCooldown = 0
    private var lastSpeed = 1.0

    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        botEvaluator = BotNodeEvaluator(auZombie)
        nodeEvaluator = botEvaluator
        return PathFinder(nodeEvaluator, maxVisitedNodes)
    }

    override fun moveTo(x: Double, y: Double, z: Double, speedModifier: Double): Boolean {
        lastSpeed = speedModifier
        return super.moveTo(x, y, z, speedModifier)
    }

    override fun tick() {
        if (ventCooldown > 0) ventCooldown--
        super.tick()
        if (ventCooldown > 0) return

        val currentPath = path?.takeUnless { it.isDone } ?: return

        val nextNode = currentPath.nextNode
        val nextKey = BlockPos.asLong(nextNode.x, nextNode.y, nextNode.z)
        if (nextKey !in botEvaluator.ventExitsByEntry) return

        val ventManager = botPlayer.game.ventManager
        val currentPos = mob.blockPosition()

        val entryVent = ventManager.vents.firstOrNull { vent ->
            val vPos = vent.normalizedLocation.run { BlockPos(blockX, blockY, blockZ) }
            currentPos.distSqr(vPos) <= BotNodeEvaluator.VENT_REACH_SQ
        } ?: return

        val exitVent = entryVent.otherVents.firstOrNull { exit ->
            val ePos = exit.normalizedLocation.run { BlockPos(blockX, blockY, blockZ) }
            BlockPos(nextNode.x, nextNode.y, nextNode.z).distSqr(ePos) <= BotNodeEvaluator.VENT_REACH_SQ
        }?.takeUnless { it === entryVent } ?: return

        val endNode = currentPath.endNode ?: return

        ventManager.ventIn(botPlayer, entryVent)
        ventManager.changeVent(botPlayer, exitVent)
        ventManager.ventOut(botPlayer)

        stop()
        ventCooldown = 40
        super.moveTo(endNode.x.toDouble(), endNode.y.toDouble(), endNode.z.toDouble(), lastSpeed)
    }
}