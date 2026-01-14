package com.fantamomo.mc.amongus.manager

import org.bukkit.entity.Entity

object EntityManager {
    private val removeOnStop = mutableListOf<Entity>()

    fun addEntityToRemoveOnStop(entity: Entity) = removeOnStop.add(entity)

    internal fun dispose() {
        for (it in removeOnStop) {
            if (!it.isValid) continue
            try {
                it.remove()
            } catch (_: Exception) {}
        }
        removeOnStop.clear()
    }
}