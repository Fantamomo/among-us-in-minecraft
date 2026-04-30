package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.game.LobbyItemManger
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.util.RefPersistentDataType
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import io.papermc.paper.event.entity.EntityKnockbackEvent
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent
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
import org.bukkit.event.player.*
import kotlin.uuid.toKotlinUuid

object PlayerListener : Listener {

    @EventHandler
    @Suppress("UnstableApiUsage")
    fun onPlayerSpawnEvent(event: AsyncPlayerSpawnLocationEvent) {
        val id = event.connection.profile.id?.toKotlinUuid() ?: return
        val lastPlayerLocation = LastPlayerLocationManager.get(id) ?: return
        val location = lastPlayerLocation.toLocation()
        if (!location.isWorldLoaded) return
        event.spawnLocation = location
        LastPlayerLocationManager.remove(id)
    }

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
        val item = event.item
        if (amongUsPlayer.game.phase == GamePhase.LOBBY || amongUsPlayer.game.phase == GamePhase.STARTING) {
            if (event.action.isRightClick && item != null) {
                if (item.persistentDataContainer.has(LobbyItemManger.LOBBY_ITEM_TYPE_KEY)) {
                    event.isCancelled = true
                    amongUsPlayer.game.lobbyItemManger.onItemUse(amongUsPlayer, item)
                    return
                }
            }
        }
        if (event.action == Action.PHYSICAL || event.action.isLeftClick) {
            event.isCancelled = true
            return
        }
        if (item?.type?.asItemType() in PlayerColor.helmetTypes) {
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
        if (!rightClicked.persistentDataContainer.has(HumanAmongUsPlayer.WARDROBE_MANNEQUIN_OWNER)) return
        val owner = rightClicked.persistentDataContainer.get(
            HumanAmongUsPlayer.WARDROBE_MANNEQUIN_OWNER,
            RefPersistentDataType.refPersistentDataType<AmongUsPlayer>()
        )?.getOrNull() ?: return
        if (usPlayer !== owner) return
        player.openInventory(WardrobeInventory(owner).inventory)
        usPlayer.game.updateAllWardrobeInventories()
    }

    @EventHandler
    fun onItemDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (item.persistentDataContainer.has(LobbyItemManger.LOBBY_ITEM_TYPE_KEY)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryInteract(event: InventoryClickEvent) {
        val inventory = event.inventory
        val holder = inventory.holder
        val player = event.whoClicked as? Player ?: return
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val phase = amongUsPlayer.game.phase
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return
        if (holder !is WardrobeInventory) {
            if (event.clickedInventory === player.inventory) {
                event.isCancelled = true
                return
            }
            return
        }
        event.isCancelled = true
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

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val auPlayer = PlayerManager.getPlayer(player) ?: return
        val game = auPlayer.game
        if (game.phase != GamePhase.REVEALING_ROLES) return
        game.roleRevealManager.onScroll(auPlayer, event.previousSlot, event.newSlot)
    }

    @EventHandler
    fun onPlayerKick(event: PlayerKickEvent) {
        if (event.cause != PlayerKickEvent.Cause.FLYING_PLAYER && event.cause != PlayerKickEvent.Cause.SELF_INTERACTION) return
        val player = event.player
        val auPlayer = PlayerManager.getPlayer(player) ?: return
        val game = auPlayer.game
        if (game.phase == GamePhase.REVEALING_ROLES) event.isCancelled = true
    }
}