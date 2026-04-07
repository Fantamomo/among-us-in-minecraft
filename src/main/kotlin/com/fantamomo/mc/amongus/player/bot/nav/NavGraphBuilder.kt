package com.fantamomo.mc.amongus.player.bot.nav

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.pathfinder.NodeEvaluator
import net.minecraft.world.level.pathfinder.PathComputationType
import org.bukkit.Location
import kotlin.math.abs

// some code in this file was written with the help of AI, some was copied and modified from nms
@NMS
class NavGraphBuilder(val game: Game) {

    fun build(): NavGraph {
        val area = game.area
        val spawn = area.gameSpawn ?: throw IllegalStateException("gameSpawn must be set before building NavGraph")
        val min = area.minCorner ?: throw IllegalStateException("minCorner must be set before building NavGraph")
        val max = area.maxCorner ?: throw IllegalStateException("maxCorner must be set before building NavGraph")
        val world = spawn.world ?: throw IllegalStateException("gameSpawn world is null")
        val level: Level = (world as org.bukkit.craftbukkit.CraftWorld).handle

        val minX = minOf(min.blockX, max.blockX)
        val maxX = maxOf(min.blockX, max.blockX)
        val minZ = minOf(min.blockZ, max.blockZ)
        val maxZ = maxOf(min.blockZ, max.blockZ)
        val minY = minOf(min.blockY, max.blockY)
        val maxY = maxOf(min.blockY, max.blockY)

        val candidates = mutableSetOf<Pair<Int, Int>>()
        val spawnX = spawn.blockX
        val spawnZ = spawn.blockZ

        var x = spawnX
        while (x >= minX) {
            addColumn(x, minZ, maxZ, candidates)
            x -= STEP
        }

        x = spawnX + STEP
        while (x <= maxX) {
            addColumn(x, minZ, maxZ, candidates)
            x += STEP
        }

        for (loc in collectSpecialLocations(area)) {
            val lx = loc.blockX
            val lz = loc.blockZ
            for (dx in -NARROW_RADIUS..NARROW_RADIUS) {
                for (dz in -NARROW_RADIUS..NARROW_RADIUS) {
                    candidates += (lx + dx) to (lz + dz)
                }
            }
        }

        val xzIndex = HashMap<Long, NavNode>()
        for ((cx, cz) in candidates) {
            if (cx !in minX..maxX || cz !in minZ..maxZ) continue
            val y = findWalkableY(level, cx, minY, maxY, cz, spawn.blockY) ?: continue
            xzIndex[xzKey(cx, cz)] = NavNode(BlockPos(cx, y, cz), NavNodeType.NORMAL)
        }

        if (xzIndex.isEmpty()) return NavGraph(emptyList())

        for (node in xzIndex.values) {
            for ((dx, dz) in ALL_OFFSETS) {
                val neighbor = xzIndex[xzKey(node.pos.x + dx, node.pos.z + dz)] ?: continue
                if (node.neighbors.any { it.node === neighbor }) continue
                if (!canConnect(level, node.pos, neighbor.pos)) continue
                val cost = if (dx != 0 && dz != 0) 14 else 10
                node.neighbors += NavNodeConnection(neighbor, cost, NavNodeConnectionType.NORMAL)
                neighbor.neighbors += NavNodeConnection(node, cost, NavNodeConnectionType.NORMAL)
            }
        }

        for (vent in game.ventManager.vents) {
            val entryNode = closestNode(xzIndex, vent.normalizedLocation.toBlockPos()) ?: continue
            for (exitVent in vent.otherVents) {
                if (exitVent === vent) continue
                val exitNode = closestNode(xzIndex, exitVent.normalizedLocation.toBlockPos()) ?: continue
                if (entryNode.neighbors.none { it.node === exitNode && it.type == NavNodeConnectionType.VENT }) {
                    entryNode.neighbors += NavNodeConnection(exitNode, 1, NavNodeConnectionType.VENT)
                }
            }
        }

        val spawnNode = closestNode(xzIndex, BlockPos(spawnX, spawn.blockY, spawnZ))
            ?: return NavGraph(xzIndex.values.toList())

        return NavGraph(pruneUnreachable(xzIndex, spawnNode).values.toList())
    }

    private fun pruneUnreachable(xzIndex: HashMap<Long, NavNode>, start: NavNode): HashMap<Long, NavNode> {
        val reachable = HashSet<NavNode>()
        val queue = ArrayDeque<NavNode>()
        reachable.add(start)
        queue.add(start)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (conn in node.neighbors) {
                if (reachable.add(conn.node)) {
                    queue.add(conn.node)
                }
            }
        }

        return HashMap(xzIndex.filter { it.value in reachable })
    }

    private fun addColumn(x: Int, minZ: Int, maxZ: Int, out: MutableSet<Pair<Int, Int>>) {
        for (z in minZ..maxZ step STEP) {
            out += x to z
        }
    }

    private fun canConnect(level: Level, from: BlockPos, to: BlockPos): Boolean {
        val yDiff = to.y - from.y
        if (abs(yDiff) > 1) return false

        val dx = to.x - from.x
        val dz = to.z - from.z
        val steps = maxOf(abs(dx), abs(dz)).coerceAtLeast(1)

        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val x = from.x + (dx * t).toInt()
            val z = from.z + (dz * t).toInt()
            val y = from.y + (yDiff * t).toInt()

            if (!isWalkable(level, x, y, z)) return false
        }

        return true
    }

    private fun findWalkableY(
        level: Level,
        x: Int,
        minY: Int,
        maxY: Int,
        z: Int,
        preferredY: Int
    ): Int? {
        for (offset in 0..6) {
            val up = preferredY + offset
            val down = preferredY - offset

            if (up <= maxY && isWalkable(level, x, up, z)) return up
            if (down >= minY && isWalkable(level, x, down, z)) return down
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun isWalkable(level: Level, x: Int, y: Int, z: Int): Boolean {
        if (!level.getBlockState(BlockPos(x, y - 1, z)).isSolid) return false
        val pos = level.getBlockState(BlockPos(x, y, z))
        val above = level.getBlockState(BlockPos(x, y + 1, z))
        return passableBlock(pos) && passableBlock(above)
    }

    private fun passableBlock(blockState: BlockState): Boolean {
        val block = blockState.block
        if (blockState.isAir) {
            return true
        } else if (!blockState.`is`(BlockTags.TRAPDOORS) && !blockState.`is`(Blocks.LILY_PAD) && !blockState.`is`(Blocks.BIG_DRIPLEAF)) {
            if (blockState.`is`(Blocks.POWDER_SNOW)) {
                return true
            } else if (!blockState.`is`(Blocks.CACTUS) && !blockState.`is`(Blocks.SWEET_BERRY_BUSH)) {
                if (blockState.`is`(Blocks.HONEY_BLOCK)) {
                    return true
                } else if (blockState.`is`(Blocks.COCOA)) {
                    return true
                } else if (!blockState.`is`(Blocks.WITHER_ROSE) && !blockState.`is`(Blocks.POINTED_DRIPSTONE)) {
                    val fluidState: FluidState = blockState.fluidState
                    if (fluidState.`is`(FluidTags.LAVA)) {
                        return false
                    } else if (NodeEvaluator.isBurningBlock(blockState)) {
                        return false
                    } else if (block is DoorBlock) {
                        return blockState.getValue(DoorBlock.OPEN)
                    } else if (block is BaseRailBlock) {
                        return true
                    } else if (block is LeavesBlock) {
                        return false
                    } else if (!blockState.`is`(BlockTags.FENCES) && !blockState.`is`(BlockTags.WALLS) && (block !is FenceGateBlock || blockState.getValue(
                            FenceGateBlock.OPEN
                        ))
                    ) {
                        return blockState.isPathfindable(PathComputationType.LAND)
                    } else {
                        return false
                    }
                } else {
                    return true
                }
            } else {
                return false
            }
        } else {
            return true
        }
    }

    private fun collectSpecialLocations(area: com.fantamomo.mc.amongus.area.GameArea): List<Location> {
        val list = mutableListOf<Location>()
        area.lobbySpawn?.let { list += it }
        area.gameSpawn?.let { list += it }
        area.meetingBlock?.let { list += it }
        area.wardrobe?.let { list += it }
        for (vg in area.vents) for (v in vg.vents) list += v.toCenterLocation()
        for ((_, locs) in area.tasks) list += locs
        return list.filter { it.world != null }
    }

    private fun closestNode(xzIndex: Map<Long, NavNode>, pos: BlockPos): NavNode? {
        val baseX = pos.x
        val baseZ = pos.z

        for (r in 0..STEP * 2) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    val node = xzIndex[xzKey(baseX + dx, baseZ + dz)]
                    if (node != null) return node
                }
            }
        }

        return null
    }

    private fun xzKey(x: Int, z: Int) = (x.toLong() and 0xFFFF_FFFFL) or ((z.toLong() and 0xFFFF_FFFFL) shl 32)

    private fun Location.toBlockPos() = BlockPos(blockX, blockY, blockZ)

    companion object {
        const val STEP = 3
        private const val NARROW_RADIUS = 2

        private val ALL_OFFSETS = listOf(
            STEP to 0, -STEP to 0,
            0 to STEP, 0 to -STEP,
            STEP to STEP, STEP to -STEP,
            -STEP to STEP, -STEP to -STEP
        )
    }
}