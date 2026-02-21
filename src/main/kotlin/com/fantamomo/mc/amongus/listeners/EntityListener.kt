package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.manager.EntityManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.persistence.PersistentDataType

object EntityListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onLoadEntities(event: EntitiesLoadEvent) {
        val entities = event.entities
        for (entity in entities) {
            val pdc = entity.persistentDataContainer
            if (!pdc.has(EntityManager.RUNTIME_ID_KEY)) continue
            val runtimeId = pdc.get(EntityManager.RUNTIME_ID_KEY, PersistentDataType.LONG)
            if (runtimeId != EntityManager.CURRENT_RUNTIME_ID) {
                entity.remove()
            }
        }
    }
}