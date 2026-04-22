package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.player.bot.nav.NavNode
import com.fantamomo.mc.amongus.player.bot.nav.NavNodeConnectionType
import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.level.pathfinder.PathFinder

@NMS
class BotPathNavigation(
    mob: AmongUsZombie,
    level: Level,
) : GroundPathNavigation(mob, level) {

    private val auZombie get() = mob as AmongUsZombie
    private val botPlayer get() = auZombie.controller.player

    private val graphPath = ArrayDeque<NavNode>()
    private var lastGraphTarget: BlockPos? = null
    private var currentSpeed = 1.0
    private var ventCooldown = 0
    private var lastSpeed = 1.0

    private var lastVisitNode: NavNode? = null

    private var navigationMode = NavigationMode.NONE


    var paused = false

    val isGraphNavigating: Boolean get() = graphPath.isNotEmpty()

    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        nodeEvaluator = BotNodeEvaluator(auZombie)
        return PathFinder(nodeEvaluator, maxVisitedNodes)
    }

    fun moveToWithGraph(target: BlockPos, speed: Double): Boolean {
        if (lastGraphTarget == target) return false
        lastGraphTarget = target
        navigationMode = NavigationMode.GRAPH
        val graph = botPlayer.game.navGraph
        val startNode = graph.findClosestNode(mob.blockPosition()) ?: return fallbackMoveTo(target, speed)
        val endNode = graph.findClosestNode(target) ?: return fallbackMoveTo(target, speed)

        val path = graph.findPath(startNode, endNode)
        if (path.isEmpty()) return fallbackMoveTo(target, speed)
        super.stop()
        graphPath.clear()
        graphPath.addAll(path)
        currentSpeed = speed
        return advanceToNextWaypoint()
    }

    private fun stopGraphNavigation() {
        graphPath.clear()
        lastVisitNode = null
        lastGraphTarget = null
    }

    override fun stop() {
        navigationMode = NavigationMode.NONE
        stopGraphNavigation()
        super.stop()
    }

    override fun tick() {
        if (paused) return

        if (ventCooldown > 0) {
            if (--ventCooldown == 0 && graphPath.isNotEmpty()) advanceToNextWaypoint()
            return
        }

        if (graphPath.isNotEmpty()) {
            if (isCloseToNode(graphPath.first())) {
                val arrived = graphPath.removeFirst()
                lastVisitNode = arrived

                if (graphPath.isEmpty()) {
                    advanceToNextWaypoint()
                    return
                }

                val next = graphPath.first()
                val connType = arrived.neighbors.find { it.node === next }?.type
                    ?: NavNodeConnectionType.NORMAL

                if (connType == NavNodeConnectionType.VENT) {
                    graphPath.removeFirst()
                    super.stop()
                    useVent(arrived, next)
                    ventCooldown = 40
                    return
                }

                advanceToNextWaypoint()
                return
            }
        }

        val lastGraphTarget = lastGraphTarget
        if (lastGraphTarget != null) {
            val dx = mob.x - (lastGraphTarget.x + 0.5)
            val dz = mob.z - (lastGraphTarget.z + 0.5)
            if (dx * dx + dz * dz <= 1.5 * 1.5) {
                stop()
            }
        }

        tick++
        if (!super.isDone) {
            if (this.canUpdatePath()) {
                this.followThePath()
            } else if (this.path != null && !this.path!!.isDone) {
                val tempMobPos = this.tempMobPos
                val nextEntityPos = this.path!!.getNextEntityPos(this.mob)
                if (tempMobPos.y > nextEntityPos.y && !this.mob.onGround() && Mth.floor(tempMobPos.x) == Mth.floor(
                        nextEntityPos.x
                    ) && Mth.floor(tempMobPos.z) == Mth.floor(nextEntityPos.z)
                ) {
                    this.path!!.advance()
                }
            }

            if (!super.isDone()) {
                val tempMobPos = this.path!!.getNextEntityPos(this.mob)
                this.mob.getMoveControl()
                    .setWantedPosition(tempMobPos.x, this.getGroundY(tempMobPos), tempMobPos.z, this.speedModifier)
            }
        }
    }

    private fun advanceToNextWaypoint(): Boolean {
        val node = graphPath.firstOrNull()
        if (node == null) {
            val target = lastGraphTarget ?: return false
            return fallbackMoveTo(target, lastSpeed)
        }
        val path = this.createPath(node.pos.x + 0.5, node.pos.y.toDouble(), node.pos.z + 0.5, 1) ?: return false
        return super.moveTo(path, currentSpeed)
    }

    override fun moveTo(x: Double, y: Double, z: Double, speedModifier: Double): Boolean {
        if (navigationMode == NavigationMode.GRAPH) return false
        stopGraphNavigation()
        navigationMode = NavigationMode.VANILLA
        lastSpeed = speedModifier
        return super.moveTo(x, y, z, speedModifier)
    }

    override fun moveTo(entity: Entity, speedModifier: Double): Boolean {
        if (navigationMode == NavigationMode.GRAPH) return false
        stopGraphNavigation()
        navigationMode = NavigationMode.VANILLA
        lastSpeed = speedModifier
        return super.moveTo(entity, speedModifier)
    }

    override fun moveTo(path: Path?, speedModifier: Double): Boolean {
        if (navigationMode == NavigationMode.GRAPH) return false
        stopGraphNavigation()
        navigationMode = NavigationMode.VANILLA
        lastSpeed = speedModifier
        return super.moveTo(path, speedModifier)
    }

    override fun moveTo(
        x: Double,
        y: Double,
        z: Double,
        reachRange: Int,
        speedModifier: Double
    ): Boolean {
        if (navigationMode == NavigationMode.GRAPH) return false
        stopGraphNavigation()
        navigationMode = NavigationMode.VANILLA
        lastSpeed = speedModifier
        return super.moveTo(x, y, z, reachRange, speedModifier)
    }

    private fun fallbackMoveTo(target: BlockPos, speed: Double): Boolean =
        super.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, speed)

    private fun isCloseToNode(node: NavNode): Boolean {
        val dx = mob.x - (node.pos.x + 0.5)
        val dz = mob.z - (node.pos.z + 0.5)
        return dx * dx + dz * dz <= 1.5 * 1.5
    }

    override fun isDone(): Boolean {
        return super.isDone() && graphPath.isEmpty() && lastGraphTarget == null
    }

    private fun useVent(from: NavNode, to: NavNode) {
        val ventManager = botPlayer.game.ventManager
        val entry = ventManager.vents.firstOrNull { v ->
            v.normalizedLocation.run { BlockPos(blockX, blockY, blockZ) }.distSqr(from.pos) <= VENT_REACH_SQ
        } ?: return
        val exit = entry.otherVents.firstOrNull { v ->
            v !== entry && v.normalizedLocation.run { BlockPos(blockX, blockY, blockZ) }
                .distSqr(to.pos) <= VENT_REACH_SQ
        } ?: return
        ventManager.ventIn(botPlayer, entry)
        ventManager.changeVent(botPlayer, exit)
        ventManager.ventOut(botPlayer)
    }

    enum class NavigationMode {
        GRAPH,
        VANILLA,
        NONE
    }

    companion object {
        private const val VENT_REACH_SQ = 4
    }
}