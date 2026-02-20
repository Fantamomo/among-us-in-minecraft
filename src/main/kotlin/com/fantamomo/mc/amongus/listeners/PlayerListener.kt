package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerColor
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.WardrobeInventory
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.util.RefPersistentDataType
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import io.papermc.paper.event.entity.EntityKnockbackEvent
import org.bukkit.GameMode
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
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

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (PlayerManager.getPlayer(player) == null) return
        val cause = event.cause
        if (cause == DamageCause.FALL || cause == DamageCause.ENTITY_ATTACK) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityAttack(event: EntityDamageEvent) {
        val mannequin = event.entity as? Mannequin ?: return
        val player = PlayerManager.getPlayer(mannequin)
        if (player != null) event.isCancelled = true
    }

    @EventHandler
    fun onEntityKnockback(event: EntityKnockbackEvent) {
        if (event.cause != EntityKnockbackEvent.Cause.PUSH) return
        val mannequin = event.entity as? Mannequin ?: return
        val player = PlayerManager.getPlayer(mannequin)
        if (player != null) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE) return
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        if (event.action == Action.PHYSICAL || event.action.isLeftClick) {
            event.isCancelled = true
            return
        }
        if (event.item?.type?.asItemType() in PlayerColor.helmetTypes) {
            event.isCancelled = true
            return
        }
        val game = amongUsPlayer.game
        val target = event.clickedBlock?.location ?: return
        val area = game.area
        if (game.sabotageManager.isSabotage(SabotageType.Lights) &&
            area.lightLevers.any { it.isSameBlockPosition(target) }
        ) return
        if (game.sabotageManager.isSabotage(SabotageType.SeismicStabilizers) &&
            (area.seismicStabilizers2?.isSameBlockPosition(target) == true ||
                    area.seismicStabilizers1?.isSameBlockPosition(target) == true)
        ) return
        if (game.sabotageManager.isSabotage(SabotageType.Communications) && area.communications?.isSameBlockPosition(
                target
            ) == true
        ) return
        if (!game.meetingManager.isCurrentlyAMeeting() && area.meetingBlock?.isSameBlockPosition(target) == true) return
        event.isCancelled = true
    }

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val usPlayer = PlayerManager.getPlayer(player) ?: return
        val game = usPlayer.game
        if (game.phase != GamePhase.LOBBY && game.phase != GamePhase.STARTING) return
        val rightClicked = event.rightClicked
        if (rightClicked !is Mannequin) return
        if (!rightClicked.persistentDataContainer.has(AmongUsPlayer.WARDROBE_MANNEQUIN_OWNER)) return
        val owner = rightClicked.persistentDataContainer.get(AmongUsPlayer.WARDROBE_MANNEQUIN_OWNER, RefPersistentDataType.refPersistentDataType<AmongUsPlayer>())?.getOrNull() ?: return
        if (usPlayer !== owner) return
        player.openInventory(WardrobeInventory(owner).inventory)
        usPlayer.game.updateAllWardrobeInventories()
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryInteract(event: InventoryClickEvent) {
        val inventory = event.inventory
        val holder = inventory.holder
        if (holder !is WardrobeInventory) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.owner.player !== player) return
        holder.onClick(event)
    }

    @EventHandler
    fun onEquipmentChange(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        PlayerManager.getPlayer(player) ?: return
        if (event.slotType != InventoryType.SlotType.ARMOR) return
        event.isCancelled = true
    }
}