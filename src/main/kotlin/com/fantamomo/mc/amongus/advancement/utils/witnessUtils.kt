package com.fantamomo.mc.amongus.advancement.utils

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.internalEntity
import com.fantamomo.mc.amongus.player.isAlive

/** The default distance in blocks in which a player can witness something. */
const val WITNESS_DISTANCE = 16.0

/**
 * True if this player is alive, within [maxDistance] blocks of [target] and has a direct line of sight to them.
 */
fun AmongUsPlayer.canSee(target: AmongUsPlayer, maxDistance: Double = WITNESS_DISTANCE): Boolean {
    if (this == target || !isAlive()) return false
    val entity = internalEntity ?: return false
    val targetEntity = target.internalEntity ?: return false
    if (entity.world != targetEntity.world) return false
    if (entity.location.distanceSquared(targetEntity.location) > maxDistance * maxDistance) return false
    return entity.hasLineOfSight(targetEntity)
}

/**
 * All players of the game (except [exclude]) that can currently see [target], see [canSee].
 */
fun witnessesOf(
    target: AmongUsPlayer,
    exclude: Collection<AmongUsPlayer> = emptyList(),
    maxDistance: Double = WITNESS_DISTANCE,
): List<AmongUsPlayer> = target.game.players.filter { it !in exclude && it.canSee(target, maxDistance) }
