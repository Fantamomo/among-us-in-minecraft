package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.manager.SabotageManager
import com.fantamomo.mc.amongus.player.PlayerManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

object SabotageListener : Listener {
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val sabotageManager = amongUsPlayer.game.sabotageManager
        val sabotage = sabotageManager.currentSabotage() ?: return
        when (sabotage) {
            SabotageManager.SabotageType.Lights -> {
                val targetBlock = event.clickedBlock ?: return
                sabotageManager.onLightLeverFlip(targetBlock.location)
            }
            else -> {}
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
        if (sabotage == SabotageManager.SabotageType.Lights) {
            val location = player.location
            sabotageManager.mayShowLightDisplayBlocks(amongUsPlayer, location)
        }
    }
}