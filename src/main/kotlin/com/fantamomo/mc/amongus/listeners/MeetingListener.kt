package com.fantamomo.mc.amongus.listeners

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.util.isBetween
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.persistence.PersistentDataType
import java.util.*

object MeetingListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.useInteractedBlock() == Event.Result.DENY) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val clickedBlock = event.clickedBlock?.location ?: return
        if (event.action == Action.PHYSICAL) return
        val meetingManager = amongUsPlayer.game.meetingManager
        if (meetingManager.isCurrentlyAMeeting()) return
        if (meetingManager.meetingBlock.isSameBlockPosition(clickedBlock)) {
            meetingManager.callMeeting(amongUsPlayer, MeetingManager.MeetingReason.BUTTON)
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val game = amongUsPlayer.game
        val meeting = game.meetingManager.meeting ?: return
        if (meeting.currentlyEjecting) {
            if (meeting.ejectedPlayer == amongUsPlayer) return
            event.isCancelled = true
        } else {
            val min = game.area.meetingRoomMin ?: return
            val max = game.area.meetingRoomMax ?: return
            if (!event.to.isBetween(min, max)) event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val meetingManager = amongUsPlayer.game.meetingManager
        if (meetingManager.isCurrentlyAMeeting()) {
            meetingManager.meeting?.onDeath(event)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.isCancelled) return
        if (event.slot != 0 && event.slot != 1) return
        val player = event.whoClicked as? Player ?: return
        val voter = PlayerManager.getPlayer(player) ?: return
        val view = event.view
        val meeting = voter.game.meetingManager.meeting ?: return
        if (view !in meeting.voteInventories.values) return
        event.isCancelled = true
        val item = event.currentItem ?: return
        if (item.persistentDataContainer.has(MeetingManager.VOTING_KEY)) {
            val target = item.persistentDataContainer.get(MeetingManager.VOTING_KEY, PersistentDataType.STRING) ?: return
            if (target == "close") {
                player.closeInventory()
                meeting.voteInventories.values.remove(view)
                return
            }
            if (target == "skip") {
                meeting.voteSkip(voter)
            } else {
                val uuid = UUID.fromString(target)
                val targetPlayer = PlayerManager.getPlayer(uuid) ?: return
                meeting.voteFor(voter, targetPlayer)
            }
            player.closeInventory()
            meeting.voteInventories.values.remove(view)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.inventory
        inventory.forEachIndexed { index, stack ->
            if (stack?.persistentDataContainer?.has(MeetingManager.VOTING_KEY) == true) {
                inventory.setItem(index, null)
            }
        }
    }

    @EventHandler
    fun onPlayerStopSpectatingEntity(event: PlayerStopSpectatingEntityEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val spectatorTarget = event.spectatorTarget
        val meetingManager = amongUsPlayer.game.meetingManager
        val meeting = meetingManager.meeting ?: return
        if (!meeting.disableEventHandler && spectatorTarget === meetingManager.cameraAnchor) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        if (event.cause != PlayerKickEvent.Cause.FLYING_PLAYER) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val meeting = amongUsPlayer.game.meetingManager.meeting ?: return
        if (meeting.currentlyEjecting) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        if (!amongUsPlayer.isAlive) return
        val game = amongUsPlayer.game
        if (game.phase != GamePhase.VOTING) return
        val meetingManager = game.meetingManager
        val meeting = meetingManager.meeting ?: return
        meeting.openVoteInventory(amongUsPlayer)
    }
}