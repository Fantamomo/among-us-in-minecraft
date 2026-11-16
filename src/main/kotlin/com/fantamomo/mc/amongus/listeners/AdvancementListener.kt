package com.fantamomo.mc.amongus.listeners

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent
import com.fantamomo.mc.amongus.advancement.AdvancementsInventory
import com.fantamomo.mc.amongus.player.PlayerManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

object AdvancementListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onCriterionGrantEvent(event: PlayerAdvancementCriterionGrantEvent) {
        val amongUsPlayer = PlayerManager.getPlayer(event.player) ?: return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryInteract(event: InventoryClickEvent) {
        val inventory = event.inventory
        val holder = inventory.holder
        if (holder !is AdvancementsInventory) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.player !== player) return
        holder.onClick(event)
    }
}