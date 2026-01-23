package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import io.papermc.paper.event.entity.EntityKnockbackEvent
import org.bukkit.GameMode
import org.bukkit.entity.Mannequin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
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
}