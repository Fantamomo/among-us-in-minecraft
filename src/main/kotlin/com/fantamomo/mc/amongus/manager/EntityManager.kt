package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.game.Game
import org.bukkit.entity.Entity
import org.slf4j.LoggerFactory

object EntityManager {
    private val logger = LoggerFactory.getLogger("AmongUsEntityManager")
    private val removeOnEnd: MutableMap<Game, MutableList<Entity>> = mutableMapOf()
    private val removeOnStop: MutableList<Entity> = mutableListOf()
    
    fun addEntityToRemoveOnEnd(game: Game, entity: Entity) = removeOnEnd.getOrPut(game) { mutableListOf() }.add(entity)

    fun addEntityToRemoveOnStop(entity: Entity) = removeOnStop.add(entity)

    internal fun dispose(game: Game) {
        removeOnEnd.remove(game)?.forEach { it.remove() }
    }

    internal fun dispose() {
        var successfullyRemoved = 0
        val totalEntities = removeOnStop.size + removeOnEnd.values.sumOf { it.size }

        val removeEntity: (Entity) -> Unit = { entity ->
            if (entity.isValid) {
                try {
                    entity.remove()
                    successfullyRemoved++
                } catch (_: Exception) {}
            }
        }

        removeOnStop.forEach(removeEntity)
        removeOnEnd.values.flatten().forEach(removeEntity)

        logger.info("Removed $successfullyRemoved/$totalEntities entities")
        removeOnStop.clear()
        removeOnEnd.clear()
    }
}