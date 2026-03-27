package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.ability.abilities.VentAbility
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.PathNavigationRegion
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.PathfindingContext
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

class BotNodeEvaluator(
    private val auZombie: AmongUsZombie,
) : WalkNodeEvaluator() {

    companion object {
        private const val VENT_ENTRY_BONUS = -2.0f
        private const val VENT_TELEPORT_COST = 1.0f
        const val VENT_REACH_SQ = 2 * 2
    }

    private val botPlayer get() = auZombie.controller.player

    var ventExitsByEntry: Map<Long, List<BlockPos>> = emptyMap()
        private set
    private var ventGraphDirty = true

    private val ventExitNodes = mutableSetOf<Long>()

    override fun prepare(level: PathNavigationRegion, mob: Mob) {
        super.prepare(level, mob)
        if (ventGraphDirty) {
            ventExitsByEntry = buildVentGraph()
            ventGraphDirty = false
        }
        ventExitNodes.clear()
    }

    override fun done() {
        super.done()
        ventExitNodes.clear()
    }

    fun invalidateVentGraph() {
        ventGraphDirty = true
    }

    private fun buildVentGraph(): Map<Long, List<BlockPos>> {
        if (!botPlayer.hasAbility(VentAbility)) return emptyMap()

        return buildMap {
            for (vent in botPlayer.game.ventManager.vents) {
                val exits = vent.otherVents
                    .filter { it !== vent }
                    .map { it.normalizedLocation.toBlockPos() }
                if (exits.isNotEmpty()) {
                    getOrPut(vent.normalizedLocation.toBlockPos().asLong()) { mutableListOf() }.addAll(exits)
                }
            }
        } as Map<Long, List<BlockPos>>
    }

    override fun getStart(): Node =
        super.getStart().also { applyVentBonusIfNeeded(it) }

    override fun getNeighbors(outputArray: Array<Node>, node: Node): Int {
        var count = super.getNeighbors(outputArray, node)

        for (i in 0 until count) {
            val n = outputArray[i]
            val key = BlockPos.asLong(n.x, n.y, n.z)
            if (key in ventExitsByEntry && key !in ventExitNodes) {
                n.costMalus = VENT_ENTRY_BONUS
            }
        }

        val nodeKey = BlockPos.asLong(node.x, node.y, node.z)
        if (nodeKey in ventExitNodes) return count

        val exits = ventExitsByEntry[nodeKey] ?: return count

        for (exit in exits) {
            if (count >= outputArray.size) break
            val exitKey = exit.asLong()

            outputArray[count++] = getNode(exit.x, exit.y, exit.z).also { exitNode ->
                exitNode.type = PathType.WALKABLE
                exitNode.costMalus = VENT_TELEPORT_COST
                ventExitNodes += exitKey
            }
        }

        return count
    }

    private fun applyVentBonusIfNeeded(node: Node) {
        val key = BlockPos.asLong(node.x, node.y, node.z)
        if (key in ventExitsByEntry && key !in ventExitNodes) {
            node.costMalus = VENT_ENTRY_BONUS
        }
    }

    private fun blockBlock(x: Int, y: Int, z: Int): Boolean {
        val game = auZombie.controller.player.game
        val meetingManager = game.meetingManager
        if (!meetingManager.isCurrentlyAMeeting()) return false
        val area = game.area
        val min = area.meetingRoomMin ?: return false
        val max = area.meetingRoomMax ?: return false
        return (min.x >= x || x >= max.x) || (min.y >= y || y >= max.y) || (min.z >= z || z >= max.z)
    }

    override fun getPathTypeOfMob(context: PathfindingContext, x: Int, y: Int, z: Int, mob: Mob): PathType =
        when {
            blockBlock(x, y, z) -> PathType.BLOCKED
            BlockPos.asLong(x, y, z) in ventExitsByEntry -> PathType.WALKABLE
            else -> super.getPathTypeOfMob(context, x, y, z, mob)
        }

    override fun getPathType(context: PathfindingContext, x: Int, y: Int, z: Int): PathType =
        when {
            blockBlock(x, y, z) -> PathType.BLOCKED
            BlockPos.asLong(x, y, z) in ventExitsByEntry -> PathType.WALKABLE
            else -> super.getPathType(context, x, y, z)
        }

    private fun org.bukkit.Location.toBlockPos() = BlockPos(blockX, blockY, blockZ)
}