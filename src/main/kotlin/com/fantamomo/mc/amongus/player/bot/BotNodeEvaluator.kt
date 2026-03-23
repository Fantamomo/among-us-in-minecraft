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
    private val mob: AmongUsZombie,
) : WalkNodeEvaluator() {

    companion object {
        private const val VENT_ENTRY_BONUS = -2.0f
        private const val VENT_TELEPORT_COST = 1.0f
        const val VENT_REACH_SQ = 2 * 2
    }

    private val botPlayer get() = mob.controller.player

    private var ventExitsByEntry: Map<Long, List<BlockPos>> = emptyMap()

    private val ventExitNodes = mutableSetOf<Long>()

    override fun prepare(level: PathNavigationRegion, mob: Mob) {
        super.prepare(level, mob)
        ventExitsByEntry = buildVentGraph()
        ventExitNodes.clear()
    }

    override fun done() {
        super.done()
        ventExitsByEntry = emptyMap()
        ventExitNodes.clear()
    }

    private fun buildVentGraph(): Map<Long, List<BlockPos>> {
        if (!botPlayer.hasAbility(VentAbility)) return emptyMap()

        val result = mutableMapOf<Long, MutableList<BlockPos>>()
        for (vent in botPlayer.game.ventManager.vents) {
            val entryPos = vent.normalizedLocation.toBlockPos()
            val exits = vent.otherVents
                .filter { it !== vent }
                .map { it.normalizedLocation.toBlockPos() }
            if (exits.isNotEmpty()) {
                result.getOrPut(entryPos.asLong()) { mutableListOf() }.addAll(exits)
            }
        }
        return result
    }

    override fun getStart(): Node {
        val node = super.getStart()
        applyVentBonusIfNeeded(node)
        return node
    }

    override fun getNeighbors(outputArray: Array<Node>, node: Node): Int {
        var count = super.getNeighbors(outputArray, node)

        for (i in 0 until count) {
            val n = outputArray[i]
            val key = BlockPos.asLong(n.x, n.y, n.z)
            if (ventExitsByEntry.containsKey(key) && !ventExitNodes.contains(key)) {
                n.costMalus = VENT_ENTRY_BONUS
            }
        }

        val nodeKey = BlockPos.asLong(node.x, node.y, node.z)
        if (ventExitNodes.contains(nodeKey)) return count

        val exits = ventExitsByEntry[nodeKey] ?: return count

        for (exit in exits) {
            if (count >= outputArray.size) break
            val exitKey = BlockPos.asLong(exit.x, exit.y, exit.z)

            val exitNode = getNode(exit.x, exit.y, exit.z)
            exitNode.type = PathType.WALKABLE
            exitNode.costMalus = VENT_TELEPORT_COST

            ventExitNodes.add(exitKey)
            outputArray[count++] = exitNode
        }

        return count
    }

    private fun applyVentBonusIfNeeded(node: Node) {
        val key = BlockPos.asLong(node.x, node.y, node.z)
        if (ventExitsByEntry.containsKey(key) && !ventExitNodes.contains(key)) {
            node.costMalus = VENT_ENTRY_BONUS
        }
    }

    override fun getPathTypeOfMob(
        context: PathfindingContext, x: Int, y: Int, z: Int, mob: Mob,
    ): PathType {
        if (ventExitsByEntry.containsKey(BlockPos.asLong(x, y, z))) return PathType.WALKABLE
        return super.getPathTypeOfMob(context, x, y, z, mob)
    }

    override fun getPathType(context: PathfindingContext, x: Int, y: Int, z: Int): PathType {
        if (ventExitsByEntry.containsKey(BlockPos.asLong(x, y, z))) return PathType.WALKABLE
        return super.getPathType(context, x, y, z)
    }

    private fun org.bukkit.Location.toBlockPos() = BlockPos(blockX, blockY, blockZ)
}