package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.sabotage.CommunicationsSabotage
import com.fantamomo.mc.amongus.sabotage.LightsSabotage
import com.fantamomo.mc.amongus.sabotage.SeismicStabilizersSabotage
import com.fantamomo.mc.amongus.util.isSameBlockPosition
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

object SabotageListener : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!event.action.isRightClick) return
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val sabotageManager = amongUsPlayer.game.sabotageManager
        val sabotage = sabotageManager.currentSabotage() ?: return
        when (sabotage) {
            is LightsSabotage -> {
                val targetBlock = event.clickedBlock ?: return
                sabotage.onLightLeverFlip(targetBlock.location, amongUsPlayer)
            }
            is CommunicationsSabotage -> {
                val targetBlock = event.clickedBlock ?: return
                if (sabotage.position.isSameBlockPosition(targetBlock.location)) {
                    sabotage.onPlayerInteract(amongUsPlayer)
                }
            }
            is SeismicStabilizersSabotage -> {
                val targetBlock = event.clickedBlock ?: return
                if (sabotage.seismicStabilizers1 == targetBlock || sabotage.seismicStabilizers2 == targetBlock) {
                    player.run {
                        sendTitlePart(TitlePart.SUBTITLE, Component.translatable("sabotage.subtitle.dead"))
                        sendTitlePart(TitlePart.TITLE, Component.translatable("sabotage.title.dead"))
                    }
                }
            }
        }
    }

//    @EventHandler
//    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
//        val player = event.entity as? Player ?: return
//        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
//        val sabotageManager = amongUsPlayer.game.sabotageManager
//        val sabotage = sabotageManager.currentSabotage() ?: return
//        when (sabotage) {
//            SabotageManager.SabotageType.Lights -> {
//                val targetBlock = event.block
//                sabotageManager.lightLevers.forEach { lever ->
//                    if (targetBlock.location.isSameBlockPosition(lever)) {
//                        sabotageManager.lightLeverFlip(lever, player, event.blockData)
//                    }
//                }
//            }
//            else -> {}
//        }
//    }

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
            if (event.hasChangedPosition()) {
                sabotage.removePlayer(amongUsPlayer)
            }
        }
    }
}