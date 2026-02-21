package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.util.internal.MorphSkinManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent

object ServerListener : Listener {

    @EventHandler
    fun onServerLoad(event: ServerLoadEvent) {
        if (event.type == ServerLoadEvent.LoadType.RELOAD) {
            // This should normally never happen.
            // Paper disables /reload when lifecycle events are registered.
            // If this executes, the server was likely forced to reload anyway.
            AmongUs.slF4JLogger.apply {
                error("")
                error("========== AMONGUS RELOAD WARNING ==========")
                error("A SERVER RELOAD WAS DETECTED.")
                error("This plugin does NOT support reloading.")
                error("")
                error("Reloading can lead to:")
                error("- Corrupted internal game state")
                error("- Broken task or scheduler execution")
                error("- Memory leaks due to duplicate classes and objects")
                error("- Inconsistent player or match data")
                error("")
                error("ACTION REQUIRED:")
                error("-> Completely STOP the server.")
                error("-> Start it again normally.")
                error(" ")
                error("DO NOT use /reload with this plugin installed.")
                error("============================================")
                error("")
            }
        }
        MorphSkinManager.init()
    }
}