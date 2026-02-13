package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.EntityManager.addEntityToRemoveOnEnd
import com.fantamomo.mc.amongus.manager.EntityManager.addEntityToRemoveOnStop
import org.bukkit.entity.Entity
import org.slf4j.LoggerFactory

/**
 * A utility object responsible for managing entities during the lifecycle
 * of a game or the complete plugin. Provides functionality to track and remove
 * entities when a game session ends or when the plugin disables.
 *
 * @author Fantamomo
 * @since 1.0-SNAPSHOT
 */
object EntityManager {
    private val logger = LoggerFactory.getLogger("AmongUsEntityManager")
    private val removeOnEnd: MutableMap<Game, MutableList<Entity>> = mutableMapOf()
    private val removeOnStop: MutableList<Entity> = mutableListOf()
    
    /**
     * Adds an entity to a list to be removed when the game ends.
     *
     * @param game The game within which the entity is to be removed at the end.
     * @param entity The entity to be added to the removal list.
     */
    fun addEntityToRemoveOnEnd(game: Game, entity: Entity) = removeOnEnd.getOrPut(game) { mutableListOf() }.add(entity)

    /**
     * Adds the specified entity to a list of entities that will be removed when the stop operation is triggered.
     *
     * @param entity The entity to be added to the list for removal upon stop.
     */
    fun addEntityToRemoveOnStop(entity: Entity) = removeOnStop.add(entity)

    /**
     * Disposes of a game instance by removing all associated resources.
     *
     * This function ensures that all resources tied to the specified `game` are properly
     * disposed of, such as removing registered callbacks or cleaning up game-related objects.
     *
     * @param game The game instance to be disposed of.
     */
    internal fun dispose(game: Game) {
        removeOnEnd.remove(game)?.forEach { it.remove() }
    }

    /**
     * Cleans up entities and resources associated with the game by removing entities from predefined collections.
     *
     * - Iterates through entities marked for removal by [addEntityToRemoveOnEnd] and [addEntityToRemoveOnStop].
     * - Checks if each entity is valid before attempting removal.
     * - Logs the count of successfully removed entities compared to the total targeted for removal.
     * - Clears the `removeOnStop` and `removeOnEnd` collections after processing.
     *
     * This function ensures that all tracked entities are properly cleaned up to prevent resource leakage.
     */
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