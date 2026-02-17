package com.fantamomo.mc.amongus.listeners

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.util.isBetween
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent

object CameraListener : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val location = event.clickedBlock?.location ?: return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val cameraManager = amongUsPlayer.game.cameraManager
        if (location.isBetween(cameraManager.cameraJoinPointMin, cameraManager.cameraJoinPointMax)) {
            if (player.isSneaking) {
                player.sendMessage(Component.translatable("camera.error.sneaking"))
                return
            }
            if (amongUsPlayer.isInGhostForm()) {
                player.sendMessage(Component.translatable("camera.error.ghost_form"))
                return
            }
            cameraManager.joinCamera(amongUsPlayer)
        }
    }

    @EventHandler
    fun onStopSpectatingEntityEvent(event: PlayerStopSpectatingEntityEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val cameraManager = amongUsPlayer.game.cameraManager
        val camera = cameraManager.getCamera(amongUsPlayer) ?: return
        if (!camera.ignorePlayerStopSpectatingEntityEvent) {
            player.isSneaking = false
            cameraManager.leaveCams(amongUsPlayer)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onClick(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val cameraManager = amongUsPlayer.game.cameraManager
        if (cameraManager.isInCams(amongUsPlayer)) {
            if (event.action.isLeftClick) cameraManager.nextCame(amongUsPlayer)
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        if (event.cause != PlayerKickEvent.Cause.SELF_INTERACTION && event.cause != PlayerKickEvent.Cause.FLYING_PLAYER) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        if (amongUsPlayer.isInCams()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        if (amongUsPlayer.isInCams()) {
            event.isCancelled = true
        }
    }
}