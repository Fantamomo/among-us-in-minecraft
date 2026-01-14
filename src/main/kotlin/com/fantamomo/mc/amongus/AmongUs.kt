package com.fantamomo.mc.amongus

import com.fantamomo.mc.amongus.area.GameAreaManager
import com.fantamomo.mc.amongus.command.AmongUsCommands
import com.fantamomo.mc.amongus.languages.LanguageManager
import com.fantamomo.mc.amongus.listeners.Listeners
import com.fantamomo.mc.amongus.manager.EntityManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

object AmongUs : JavaPlugin() {

    const val IN_DEVELOPMENT = true

    override fun onEnable() {
        GameAreaManager.loadAreas()
        Listeners.registerAll()
        LanguageManager.init()

        AmongUs.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            AmongUsCommands.registerAll(it.registrar())
        }
    }

    override fun onDisable() {
        GameAreaManager.saveAll()
        EntityManager.dispose()
    }
}
