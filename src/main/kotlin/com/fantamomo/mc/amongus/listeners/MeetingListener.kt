package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent

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
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val meetingManager = amongUsPlayer.game.meetingManager
        if (meetingManager.isCurrentlyAMeeting()) {
            meetingManager.meeting?.onDeath(event)
        }
    }
}