package com.fantamomo.mc.amongus

import com.fantamomo.mc.amongus.area.GameAreaManager
import com.fantamomo.mc.amongus.command.AmongUsCommands
import com.fantamomo.mc.amongus.command.Permissions
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import com.fantamomo.mc.amongus.languages.LanguageManager
import com.fantamomo.mc.amongus.listeners.Listeners
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.PlayerDataManager
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.statistics.StatisticsManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

/**
 * An object that serves as the main entry point for the AmongUs plugin.
 * This object extends [JavaPlugin] for integration with the Minecraft server.
 *
 * We use here an `object` instead of a `class` so we don't need to provide to each class an instance of it via the constructor.
 * And can use in other classes directly [AmongUs].
 *
 * @see AmongUsBootstrapper
 */
object AmongUs : JavaPlugin() {

    const val IN_DEVELOPMENT = true

    override fun onEnable() {
        saveDefaultConfig()
        if (IN_DEVELOPMENT) with(slF4JLogger) {
            info("This Plugin is running in development mode!")
            info("In developing mode, some features work not as expected.")
            info("This is for easier developing.")
        }
        GameAreaManager.loadAreas()
        Listeners.registerAll()
        LanguageManager.init()
        AmongUsSecrets.init()

        AmongUs.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            AmongUsCommands.init(it.registrar())
        }

        Permissions.registerAll()

        runCatching { classLoader.loadClass("kotlin.io.FilesKt") }
    }

    private val classNotFoundExceptions: MutableList<NoClassDefFoundError> = mutableListOf()

    override fun onDisable() {
        classNotFoundExceptions.clear()
        // Lambda expressions are used instead of method references to defer class loading.
        // This prevents NoClassDefFoundError from being thrown outside the try-catch block
        // in saveRun when classes like EntityManager haven't been loaded yet.
        // Method references (e.g., EntityManager::dispose) would trigger class loading
        // at the call site before entering the saveRun function.
        saveRun { PlayerManager.stop() }
        saveRun { EntityManager.dispose() }
        saveRun { MeetingManager.dispose() }
        saveRun { StatisticsManager.saveAll() }
        saveRun { GameAreaManager.saveAll() }
        saveRun { PlayerDataManager.saveAll() }

        val ex = classNotFoundExceptions
        if (ex.isNotEmpty()) {
            with(slF4JLogger) {
                error("${if (ex.size == 1) "A" else ex.size} NoClassDefFoundError occurred during plugin shutdown.")
                error("The plugin was most likely hot-reloaded or the JAR was replaced while the server was running.")
                error("")
                error("This is NOT supported and breaks the plugin's classloader.")
                error("As a result, data was VERY LIKELY not saved correctly and may be permanently lost.")
                error("")
                error("We do NOT provide support for any issues or data loss caused by hot-reloading.")
                error("")
                if (ex.size == 1) error("Exception details:", ex.first())
                else ex.forEachIndexed { index, error ->
                    error("Exception details ({}):", index, error)
                }
            }
        }
    }

    private inline fun saveRun(block: () -> Unit) {
        try {
            block()
        } catch (e: NoClassDefFoundError) {
            classNotFoundExceptions += e
        } catch (e: Exception) {
            slF4JLogger.error("An unexpected error occurred while saving data", e)
        }
    }
}
