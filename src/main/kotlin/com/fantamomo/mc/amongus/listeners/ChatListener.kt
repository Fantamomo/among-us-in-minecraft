@file:Suppress("DEPRECATION")

package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.player.PlayerManager
import io.papermc.paper.event.player.ChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object ChatListener : Listener {

    @EventHandler
    fun onChat(event: ChatEvent) {
        val player = event.player
        val amongUsPlayer = PlayerManager.getPlayer(player) ?: return
        val chatManager = amongUsPlayer.game.chatManager
        chatManager.onChat(amongUsPlayer, event)
    }
}