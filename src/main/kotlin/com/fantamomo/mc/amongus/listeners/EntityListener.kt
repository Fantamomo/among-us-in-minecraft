package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import com.fantamomo.mc.amongus.util.internal.NMS
import org.bukkit.craftbukkit.entity.CraftZombie
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
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

    @NMS
    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity !is CraftZombie) return
        val handle = entity.handle
        if (handle !is AmongUsZombie) return
        event.isCancelled = true
    }

    @NMS
    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity !is CraftZombie) return
        val handle = entity.handle
        if (handle !is AmongUsZombie) return
        event.isCancelled = true
    }
}