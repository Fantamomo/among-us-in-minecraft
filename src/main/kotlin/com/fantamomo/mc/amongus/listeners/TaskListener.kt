package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.GuiAssignedTask.Companion.MOVEABLE_ITEM_KEY
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object TaskListener : Listener {
    private fun ItemStack.isMoveable() = persistentDataContainer.has(MOVEABLE_ITEM_KEY, PersistentDataType.BYTE)

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.useInteractedBlock() == Event.Result.DENY || event.action == Action.PHYSICAL) return
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
        PlayerManager.getPlayer(player) ?: return
        val currentItem = event.currentItem
        val cursorItem = event.cursor
        val task = event.clickedInventory?.holder as? GuiAssignedTask<*, *>
        if (task == null) {
            if (!event.isShiftClick) return
            if (event.view.topInventory is GuiAssignedTask<*, *>) {
                event.isCancelled = true
            }
            return
        }
        if (!(cursorItem.isMoveable() && currentItem?.isEmpty != false) &&
            !(currentItem?.isMoveable() == true && cursorItem.isEmpty) &&
            !(currentItem?.isMoveable() == true && cursorItem.isMoveable())
        ) {
            event.isCancelled = true
        }
        task.onInventoryClick(event)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryHotbar(event: InventoryClickEvent) {
        if (event.action != InventoryAction.HOTBAR_SWAP) return
        if (event.hotbarButton == -1) return
        val player = event.whoClicked as? Player ?: return
        PlayerManager.getPlayer(player) ?: return
        val task = event.clickedInventory?.holder as? GuiAssignedTask<*, *> ?: return
        event.isCancelled = true
        task.onHotbarButton(event.hotbarButton)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.reason == InventoryCloseEvent.Reason.PLUGIN) return
        val player = event.player as? Player ?: return
        PlayerManager.getPlayer(player) ?: return
        val task = event.inventory.holder as? GuiAssignedTask<*, *> ?: return
        task.onInventoryClose()
        player.inventory.forEachIndexed { index, stack ->
            if (stack?.isMoveable() == true) player.inventory.setItem(index, null)
        }
        if (player.itemOnCursor.isMoveable()) {
            player.setItemOnCursor(null)
        }
    }

    @EventHandler
    fun onItemDrop(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack.isMoveable()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDragEvent(event: InventoryDragEvent) {
        if (event.isCancelled) return
        val player = event.whoClicked as? Player ?: return
        PlayerManager.getPlayer(player) ?: return
        val cursorItem = event.cursor
        val newItems = event.newItems
        val task = event.inventory.holder as GuiAssignedTask<*, *>
        if (cursorItem?.isMoveable() != true && !newItems.any { it.value.isMoveable() }) {
            event.isCancelled = true
        }
        task.onInventoryDrag(event)
    }
}