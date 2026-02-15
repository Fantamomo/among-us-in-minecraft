package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.manager.MorphManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

object RoleListener : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val inventory = event.clickedInventory ?: return
        val holder = inventory.holder ?: return
        if (holder is MorphManager.MorphInventory) {
            event.isCancelled = true
            holder.onClick(event)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.inventory
        val holder = inventory.holder
        if (holder is MorphManager.MorphInventory) {
            holder.onClose(event)
        }
    }
}