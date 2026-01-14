package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

object VentListener : Listener {

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = PlayerManager.getPlayer(event.player) ?: return
        if (player.isVented()) {
            if (event.hasChangedPosition()) {
                event.isCancelled = true
            } else if (event.hasChangedOrientation()) {
                player.game.ventManager.orientationChange(player, event.to)
            }
        }
    }
}