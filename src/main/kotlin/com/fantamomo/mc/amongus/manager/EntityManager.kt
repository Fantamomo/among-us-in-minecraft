package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.EntityManager.addEntityToRemoveOnEnd
import com.fantamomo.mc.amongus.manager.EntityManager.addEntityToRemoveOnStop
import com.fantamomo.mc.amongus.manager.EntityManager.dispose
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
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
    val RUNTIME_ID_KEY = NamespacedKey(AmongUs, "runtime_id")
    /**
     * Represents a unique identifier for the current runtime session.
     *
     * This ID is generated at the time of variable initialization using the system's current timestamp.
     * It is used to differentiate between runtime sessions and help in cleanup procedures,
     * removing entities from previous sessions in cases where the [dispose] function is not called.
     *
     * @see com.fantamomo.mc.amongus.listeners.EntityListener.onLoadEntities
     */
    val CURRENT_RUNTIME_ID: Long = System.currentTimeMillis()

    /**
     * Adds an entity to a list to be removed when the game ends.
     *
     * @param game The game within which the entity is to be removed at the end.
     * @param entity The entity to be added to the removal list.
     */
    fun addEntityToRemoveOnEnd(game: Game, entity: Entity) =
        removeOnEnd.getOrPut(game) { mutableListOf() }.add(entity.addRuntimeId())

    /**
     * Adds the specified entity to a list of entities that will be removed when the stop operation is triggered.
     *
     * @param entity The entity to be added to the list for removal upon stop.
     */
    fun addEntityToRemoveOnStop(entity: Entity) = removeOnStop.add(entity.addRuntimeId())

    private fun Entity.addRuntimeId(): Entity = apply {
        persistentDataContainer.set(RUNTIME_ID_KEY, PersistentDataType.LONG, CURRENT_RUNTIME_ID)
    }

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

        removeOnStop.forEach {
            if (it.isValid) {
                try {
                    it.remove()
                    successfullyRemoved++
                } catch (_: Exception) {}
            }
        }
        removeOnEnd.values.flatten().forEach {
            if (it.isValid) {
                try {
                    it.remove()
                    successfullyRemoved++
                } catch (_: Exception) {}
            }
        }

        logger.info("Removed $successfullyRemoved/$totalEntities entities")
        removeOnStop.clear()
        removeOnEnd.clear()
    }
}