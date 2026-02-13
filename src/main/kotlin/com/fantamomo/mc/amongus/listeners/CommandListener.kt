package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.player.PlayerManager
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

object CommandListener : Listener {
    @EventHandler
    fun onCommandPreProgress(event: PlayerCommandPreprocessEvent) {
        if (!AmongUsConfig.MsgCommandBlocker.disabled && AmongUsConfig.MsgCommandBlocker.legacy) {
            val sender = event.player
            if (PlayerManager.getPlayer(sender) == null) return
            val blocked = AmongUsConfig.MsgCommandBlocker.commands
            val message = event.message.removePrefix("/").split(" ", limit = 2).first()
            if (blocked.contains(message)) {
                event.isCancelled = true
                event.player.sendMessage(Component.translatable("command.error.msg.in_game"))
            }
        }
    }
}