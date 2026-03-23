package com.fantamomo.mc.amongus.player.bot

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.PathFinder

class BotPathNavigation(
    mob: AmongUsZombie,
    level: Level,
) : GroundPathNavigation(mob, level) {

    private val botPlayer get() = (mob as AmongUsZombie).controller.player
    private lateinit var botEvaluator: BotNodeEvaluator

    private var ventCooldown = 0

    private var lastSpeed = 1.0

    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        botEvaluator = BotNodeEvaluator(mob as AmongUsZombie)
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

        val currentPath = path ?: return
        if (currentPath.isDone) return

        val nextNode = currentPath.nextNode ?: return
        val nextPos = BlockPos(nextNode.x, nextNode.y, nextNode.z)

        val ventManager = botPlayer.game.ventManager
        val currentPos = mob.blockPosition()

        val entryVent = ventManager.vents.find { vent ->
            val vPos = vent.normalizedLocation.run { BlockPos(blockX, blockY, blockZ) }
            currentPos.distSqr(vPos) <= BotNodeEvaluator.VENT_REACH_SQ.toLong()
        } ?: return

        val exitVent = entryVent.otherVents
            .find { exit ->
                val ePos = exit.normalizedLocation.run { BlockPos(blockX, blockY, blockZ) }
                nextPos.distSqr(ePos) <= BotNodeEvaluator.VENT_REACH_SQ.toLong()
            } ?: return

        if (exitVent === entryVent) return

        val endNode = currentPath.endNode ?: return
        val targetX = endNode.x.toDouble()
        val targetY = endNode.y.toDouble()
        val targetZ = endNode.z.toDouble()

        ventManager.ventIn(botPlayer, entryVent)
        ventManager.changeVent(botPlayer, exitVent)
        ventManager.ventOut(botPlayer)

        stop()
        ventCooldown = 40

        super.moveTo(targetX, targetY, targetZ, lastSpeed)
    }
}