package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.settings.SettingsInventory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

object SettingsListener : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryInteract(event: InventoryClickEvent) {
        val inventory = event.inventory
        val holder = inventory.holder
        if (holder !is SettingsInventory) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.owner.player !== player) return
        holder.onClick(event)
    }
}