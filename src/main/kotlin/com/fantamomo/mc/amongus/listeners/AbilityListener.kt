package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.ability.AbilityManager
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack

object AbilityListener : Listener {

    private fun ItemStack.isAbilityItem() = AbilityManager.isAbilityItem(this)

    @EventHandler(priority = EventPriority.NORMAL)
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

    /**
     * Prevents ability items from being moved, dropped, or transferred
     * in unintended ways.
     *
     * Insight:
     * Ability items are considered system-critical items and must remain inside the
     * player's own inventory. This handler therefore blocks:
     *
     * - Moving ability items into other inventories (e.g. chests, furnaces, etc.)
     * - Dropping them (cursor or slot drops)
     * - Collect-to-cursor behavior
     * - Bundle interactions
     * - Shift-click transfers into foreign inventories
     * - Hotbar swaps that would move ability items across inventory boundaries
     *
     * Only a minimal, explicitly whitelisted set of safe inventory actions is allowed
     * (e.g., pickup/place/swap within the player's own inventory).
     *
     * This ensures ability items remain stable, non-transferable, and non-exploitable
     * while still allowing normal inventory interaction inside the player inventory.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val playerInv = player.inventory

        val clickedInv = event.clickedInventory
        val action = event.action

        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        var abilityInvolved =
            (clickedItem?.isAbilityItem() == true) || cursorItem.isAbilityItem()

        if (action == InventoryAction.HOTBAR_SWAP) {
            if (clickedInv == playerInv) return
            val button = event.hotbarButton
            val hotbarItem =
                if (button == -1) playerInv.itemInOffHand
                else playerInv.getItem(button)

            if (hotbarItem?.isAbilityItem() == true) {
                abilityInvolved = true
            }
        }

        if (!abilityInvolved) return

        val topInventory = player.openInventory.topInventory
        if (event.isShiftClick && topInventory.type == InventoryType.CRAFTING && topInventory.holder == player && event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return
        }
        if (event.isShiftClick && topInventory != playerInv) {
            event.isCancelled = true
            return
        }

        if (clickedInv != null && clickedInv != playerInv) {
            event.isCancelled = true
            return
        }

        when (action) {

            InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT,

            InventoryAction.MOVE_TO_OTHER_INVENTORY,

            InventoryAction.CLONE_STACK,
            InventoryAction.COLLECT_TO_CURSOR,

            InventoryAction.UNKNOWN,

            InventoryAction.PICKUP_FROM_BUNDLE,
            InventoryAction.PICKUP_ALL_INTO_BUNDLE,
            InventoryAction.PICKUP_SOME_INTO_BUNDLE,
            InventoryAction.PLACE_FROM_BUNDLE,
            InventoryAction.PLACE_ALL_INTO_BUNDLE,
            InventoryAction.PLACE_SOME_INTO_BUNDLE -> {
                event.isCancelled = true
                return
            }

            else -> {
            }
        }

        if (cursorItem.isAbilityItem()) {
            if (clickedInv == null || clickedInv != playerInv) {
                event.isCancelled = true
                return
            }
        }

        val allowedActions = setOf(
            InventoryAction.NOTHING,
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_SOME,
            InventoryAction.PLACE_ONE,
            InventoryAction.SWAP_WITH_CURSOR
        )

        if (action !in allowedActions) {
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