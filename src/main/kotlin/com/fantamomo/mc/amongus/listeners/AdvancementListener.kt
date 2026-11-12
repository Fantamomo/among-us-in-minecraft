package com.fantamomo.mc.amongus.listeners

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent
import com.fantamomo.mc.amongus.player.PlayerManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

object AdvancementListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onCriterionGrantEvent(event: PlayerAdvancementCriterionGrantEvent) {
        val amongUsPlayer = PlayerManager.getPlayer(event.player) ?: return
        event.isCancelled = true
    }
}