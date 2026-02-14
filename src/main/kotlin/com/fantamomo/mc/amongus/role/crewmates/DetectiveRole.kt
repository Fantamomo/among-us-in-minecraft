package com.fantamomo.mc.amongus.role.crewmates

import com.destroystokyo.paper.ParticleBuilder
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.Location
import org.bukkit.Particle

object DetectiveRole : Role<DetectiveRole, DetectiveRole.AssignedDetectiveRole> {
    override val id: String = "detective"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedDetectiveRole(player)

    class AssignedDetectiveRole(
        override val player: AmongUsPlayer
    ) : AssignedRole<DetectiveRole, AssignedDetectiveRole> {

        override val definition = DetectiveRole

        private data class TrailPoint(
            val location: Location,
            val expireTick: Int
        )

        private val trails = mutableMapOf<AmongUsPlayer, ArrayDeque<TrailPoint>>()
        private val particleBuilder = ParticleBuilder(Particle.DUST)

        private var currentTick = 0

        override fun onGameStart() {
            trails.clear()
            for (p in player.game.players) {
                if (p != player) {
                    trails[p] = ArrayDeque()
                }
            }
        }

        override fun onGameEnd() {
            trails.clear()
        }

        override fun tick() {
            currentTick++
            if (currentTick % 2 != 0) return

            val viewer = player.player ?: return
            val viewerLoc = viewer.location

            for ((other, trail) in trails) {

                val entityLoc = other.mannequinController
                    .getEntity()
                    ?.location

                if (entityLoc != null && other.isAlive && !other.isVented()) {
                    trail.addLast(
                        TrailPoint(
                            entityLoc.clone(),
                            currentTick + TTL
                        )
                    )
                }

                while (trail.isNotEmpty() && trail.first().expireTick <= currentTick) {
                    trail.removeFirst()
                }

                val color = other.color.color

                for (point in trail) {
                    if (point.location.distanceSquared(viewerLoc) > MAX_DISTANCE) continue

                    particleBuilder
                        .location(point.location)
                        .color(color)
                        .receivers(viewer)
                        .spawn()
                }
            }
        }

        companion object {
            private const val MAX_DISTANCE = 25 * 25
            private const val TTL = 100
        }
    }
}