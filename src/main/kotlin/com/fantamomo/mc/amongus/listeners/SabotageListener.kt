package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.sabotage.CommunicationsSabotage
import com.fantamomo.mc.amongus.sabotage.LightsSabotage
import com.fantamomo.mc.amongus.sabotage.SeismicStabilizersSabotage
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Material
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

object SabotageListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.useInteractedBlock() == Event.Result.DENY) return
        if (!event.action.isRightClick) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val sabotageManager = amongUsPlayer.game.sabotageManager
        val sabotage = sabotageManager.currentSabotage() ?: return
        when (sabotage) {
            is LightsSabotage -> {
                val targetBlock = event.clickedBlock ?: return
                if (!sabotage.onLightLeverFlip(targetBlock.location, amongUsPlayer)) {
                    event.isCancelled = true
                }
            }
            is CommunicationsSabotage -> {
                val targetBlock = event.clickedBlock ?: return
                if (sabotage.position.isSameBlockPosition(targetBlock.location)) {
                    sabotage.onPlayerInteract(amongUsPlayer)
                }
            }
            is SeismicStabilizersSabotage -> {
                if (amongUsPlayer.isAlive) return
                val targetBlock = event.clickedBlock ?: return
                if (sabotage.seismicStabilizers1 == targetBlock || sabotage.seismicStabilizers2 == targetBlock) {
                    player.run {
                        sendTitlePart(TitlePart.SUBTITLE, Component.translatable("sabotage.subtitle.dead"))
                        sendTitlePart(TitlePart.TITLE, Component.translatable("sabotage.title.dead"))
                    }
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        if (event.newCurrent != 0) return
        val block = event.block
        for (game in GameManager.getGames()) {
            val sabotage = game.sabotageManager.currentSabotage()
            if (sabotage is LightsSabotage) {
                if (block.type == Material.IRON_TRAPDOOR) {
                    event.newCurrent = 15
                    return
                }
            }
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val sabotageManager = amongUsPlayer.game.sabotageManager
        val sabotage = sabotageManager.currentSabotage() ?: return
        if (sabotage is LightsSabotage) {
            val location = player.location
            sabotage.mayShowLightDisplayBlocks(amongUsPlayer, location)
        } else if (sabotage is CommunicationsSabotage) {
            if (!sabotage.canMoveAndDisable(amongUsPlayer) && event.hasChangedPosition()) {
                sabotage.removePlayer(amongUsPlayer)
            }
        }
    }
}