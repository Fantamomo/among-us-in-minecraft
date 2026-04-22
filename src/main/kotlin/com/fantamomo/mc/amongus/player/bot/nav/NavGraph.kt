package com.fantamomo.mc.amongus.player.bot.nav

import com.destroystokyo.paper.ParticleBuilder
import net.minecraft.core.BlockPos
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import java.util.PriorityQueue
import kotlin.collections.ArrayDeque

class NavGraph(val nodes: List<NavNode>) {
    private val buckets: Map<Long, List<NavNode>> = buildMap<Long, MutableList<NavNode>> {
        for (node in nodes) {
            getOrPut(bucketKey(node.pos.x, node.pos.z)) { mutableListOf() }.add(node)
        }
    }

    private fun bucketKey(x: Int, z: Int): Long {
        val bx = Math.floorDiv(x, BUCKET).toLong() and 0xFFFF_FFFFL
        val bz = Math.floorDiv(z, BUCKET).toLong() and 0xFFFF_FFFFL
        return bx or (bz shl 32)
    }

    fun findClosestNode(pos: BlockPos): NavNode? {
        if (nodes.isEmpty()) return null
        val originBx = Math.floorDiv(pos.x, BUCKET)
        val originBz = Math.floorDiv(pos.z, BUCKET)
        var best: NavNode? = null
        var bestDistSq = Double.MAX_VALUE
        for (radius in 0..512) {
            var foundAny = false
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue
                    val key = bucketKey((originBx + dx) * BUCKET, (originBz + dz) * BUCKET)
                    val bucket = buckets[key] ?: continue
                    foundAny = true
                    for (node in bucket) {
                        if (node.type == NavNodeType.BLOCKED) continue
                        val d = node.pos.distSqr(pos)
                        if (d < bestDistSq) {
                            bestDistSq = d; best = node
                        }
                    }
                }
            }
            if (best != null) {
                val minNextDist = ((radius * BUCKET) - BUCKET).coerceAtLeast(0)
                    .toLong()
                if (minNextDist * minNextDist > bestDistSq) break
            }
            if (!foundAny && best != null) break
        }
        return best
    }

    fun findPath(start: NavNode, end: NavNode): List<NavNode> {
        if (start === end) return listOf(start)
        val gScore = HashMap<NavNode, Int>()
        val cameFrom = HashMap<NavNode, NavNode>()
        val closed = HashSet<NavNode>()
        gScore[start] = 0
        val open = PriorityQueue<NavNode>(compareBy {
            (gScore[it] ?: Int.MAX_VALUE) + it.pos.distManhattan(end.pos)
        })
        open.add(start)
        while (open.isNotEmpty()) {
            val current = open.poll()
            if (current === end) {
                return buildPath(cameFrom, end)
            }
            if (!closed.add(current)) continue
            val g = gScore[current] ?: continue
            for (conn in current.neighbors) {
                val nb = conn.node
                if (nb in closed || nb.type == NavNodeType.BLOCKED) continue
                val tentative = g + conn.cost
                if (tentative < (gScore[nb] ?: Int.MAX_VALUE)) {
                    gScore[nb] = tentative
                    cameFrom[nb] = current
                    open.add(nb)
                }
            }
        }
        return emptyList()
    }

    private fun buildPath(cameFrom: Map<NavNode, NavNode>, end: NavNode): List<NavNode> {
        val path = ArrayDeque<NavNode>()
        var cur: NavNode? = end
        while (cur != null) {
            path.addFirst(cur)
            cur = cameFrom[cur]
        }
        return path.toList()
    }

    fun debugShowGraph(player: Player, radius: Double = 64.0) {
        val world = player.world

        val px = player.location.x
        val py = player.location.y
        val pz = player.location.z

        val radiusSq = radius * radius

        red.receivers(player)
        lime.receivers(player)
        blue.receivers(player)

        fun spawn(x: Double, y: Double, z: Double, builder: ParticleBuilder, size: Float = 0.2f) {
            builder.location(world, x, y, z)
            builder.spawn()
        }

        fun drawLine(
            aX: Double, aY: Double, aZ: Double,
            bX: Double, bY: Double, bZ: Double,
            builder: ParticleBuilder
        ) {
            val steps = 8
            for (i in 0..steps) {
                val t = i / steps.toDouble()
                val x = aX + (bX - aX) * t
                val y = aY + (bY - aY) * t
                val z = aZ + (bZ - aZ) * t
                spawn(x, y, z, builder, 0.15f)
            }
        }

        val nodeColor = lime
        val edgeColor = blue
        val blockedColor = red

        for (node in nodes) {
            val dx = node.pos.x + 0.5 - px
            val dy = node.pos.y + 0.5 - py
            val dz = node.pos.z + 0.5 - pz

            if (dx * dx + dy * dy + dz * dz > radiusSq) continue

            val color = if (node.type == NavNodeType.BLOCKED) blockedColor else nodeColor

            spawn(node.pos.x + 0.5, node.pos.y + 0.5, node.pos.z + 0.5, color, 0.25f)

            if (node.type == NavNodeType.BLOCKED) continue

            for (conn in node.neighbors) {
                val nb = conn.node

                val ndx = nb.pos.x + 0.5 - px
                val ndy = nb.pos.y + 0.5 - py
                val ndz = nb.pos.z + 0.5 - pz

                if (ndx * ndx + ndy * ndy + ndz * ndz > radiusSq) continue

                drawLine(
                    node.pos.x + 0.5, node.pos.y + 0.5, node.pos.z + 0.5,
                    nb.pos.x + 0.5, nb.pos.y + 0.5, nb.pos.z + 0.5,
                    edgeColor
                )
            }
        }
    }

    companion object {
        private const val BUCKET = 16
        private val blue: ParticleBuilder = Particle.DUST.builder()
            .color(Color.BLUE, 2.0f)
        private val lime: ParticleBuilder = Particle.DUST.builder()
            .color(Color.LIME, 2.0f)
        private val red: ParticleBuilder = Particle.DUST.builder()
            .color(Color.RED, 2.0f)
    }
}