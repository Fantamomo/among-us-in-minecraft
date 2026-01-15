package com.fantamomo.mc.amongus.manager

import org.bukkit.entity.Entity
import org.slf4j.LoggerFactory

object EntityManager {
    private val logger = LoggerFactory.getLogger("AmongUsEntityManager")
    private val removeOnStop = mutableListOf<Entity>()

    fun addEntityToRemoveOnStop(entity: Entity) = removeOnStop.add(entity)

    internal fun dispose() {
        var successfullyRemoved: Int = 0
        for (it in removeOnStop) {
            if (!it.isValid) continue
            try {
                it.remove()
                successfullyRemoved++
            } catch (_: Exception) {}
        }
        logger.info("Removed $successfullyRemoved/${removeOnStop.size} entities")
        removeOnStop.clear()
    }
}