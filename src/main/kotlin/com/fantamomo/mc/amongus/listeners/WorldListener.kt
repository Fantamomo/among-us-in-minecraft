package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.area.GameAreaManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldInitEvent

object WorldListener : Listener {
    @EventHandler
    fun onWorldLoad(event: WorldInitEvent) {
        val world = event.world
        val areas = GameAreaManager.getAreas(world)
        val worldContainer = AmongUs.server.worldContainer.toPath().toAbsolutePath()
        areas.forEach { area ->
            area.worldFolder = worldContainer.relativize(world.worldPath.toAbsolutePath()).toString()
        }
    }
}