package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.ability.AbilityManager
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack

object AbilityListener : Listener {

    private fun ItemStack.isAbilityItem() = AbilityManager.isAbilityItem(this)

    @EventHandler(priority = EventPriority.HIGH)
    fun onItemUse(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL) return
        val item = event.item ?: return
        if (item.isAbilityItem()) {
            if (event.action.isRightClick) {
                if (AbilityManager.itemRightClick(item, event.player)) {
                    event.isCancelled = true
                }
            } else {
                if (AbilityManager.itemLeftClick(item, event.player)) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onItemDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (item.isAbilityItem()) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryItemPickup(event: InventoryPickupItemEvent) {
        val item = event.item.itemStack
        if (item.isAbilityItem()) {
            event.isCancelled = true
            event.item.remove()
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onItemPickup(event: PlayerAttemptPickupItemEvent) {
        val item = event.item.itemStack
        if (item.isAbilityItem()) {
            event.isCancelled = true
            event.item.remove()
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        if (!event.item.isAbilityItem()) return
        if (event.destination.holder !is Player && event.destination.viewers.none { it is Player }) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.cursor?.isAbilityItem() == true || event.newItems.any { it.value.isAbilityItem() }) {
            event.result = Event.Result.DENY
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val playerInventory = player.inventory

        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        val isAbilityInvolved = (clickedItem?.isAbilityItem() == true) || cursorItem.isAbilityItem()

        if (!isAbilityInvolved) return

        val clickedInventory = event.clickedInventory

        if (clickedInventory != null && (clickedInventory != playerInventory || event.isShiftClick)) {
            event.isCancelled = true
        } else if (clickedInventory != null && event.isRightClick) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val inventory = event.player.inventory
        val indexes =
            inventory.mapIndexedNotNull { index, stack -> index.takeIf { stack?.isAbilityItem() == true } }
        indexes.forEach { inventory.setItem(it, null) }
    }
}