package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent

object TaskListener : Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.useInteractedBlock() == Event.Result.DENY) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val location = event.clickedBlock?.location ?: return
        if (amongUsPlayer.game.taskManager.startTask(amongUsPlayer, location)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.isCancelled) return
        val player = event.whoClicked as? Player ?: return
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val task = event.clickedInventory?.holder as? GuiAssignedTask<*, *> ?: return
        event.isCancelled = true
        task.onInventoryClick(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.reason == InventoryCloseEvent.Reason.PLUGIN) return
        val player = event.player as? Player ?: return
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val task = event.inventory.holder as? GuiAssignedTask<*, *> ?: return
        task.onInventoryClose()
    }
}