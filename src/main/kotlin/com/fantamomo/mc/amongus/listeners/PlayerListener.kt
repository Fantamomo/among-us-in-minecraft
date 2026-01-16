package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import org.bukkit.entity.Mannequin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object PlayerListener : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        PlayerManager.onPlayerJoin(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        PlayerManager.onPlayerQuit(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityAttack(event: EntityDamageEvent) {
        val mannequin = event.entity as? Mannequin ?: return
        val player = PlayerManager.getPlayer(mannequin)
        if (player != null) event.isCancelled = true
    }
}