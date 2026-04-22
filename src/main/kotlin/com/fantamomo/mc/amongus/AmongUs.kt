package com.fantamomo.mc.amongus

import com.fantamomo.mc.amongus.ai.AiService
import com.fantamomo.mc.amongus.area.GameAreaManager
import com.fantamomo.mc.amongus.command.AmongUsCommands
import com.fantamomo.mc.amongus.command.Permissions
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import com.fantamomo.mc.amongus.languages.LanguageManager
import com.fantamomo.mc.amongus.listeners.Listeners
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.LastPlayerLocationManager
import com.fantamomo.mc.amongus.player.PlayerDataManager
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.statistics.StatisticsManager
import com.fantamomo.mc.amongus.util.BotsJoinMessages
import com.fantamomo.mc.amongus.util.JarType
import com.fantamomo.mc.amongus.util.LogFilter
import com.fantamomo.mc.amongus.util.applyUnless
import com.fantamomo.mc.amongus.util.log.ActionLogManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    internal val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
        slF4JLogger.error("Unhandled exception in AmongUs coroutine scope", throwable)
    })

    override fun onLoad() {
        val inDevelopment = AmongUsConstants.IN_DEVELOPMENT
        val unattached = AmongUsConstants.UNATTACHED
        with(slF4JLogger) {
            info(buildString {
                append("Running v")
                append(pluginMeta.version)
                AmongUsConstants.GIT_HASH
                    ?.applyUnless(inDevelopment || unattached != false) { take(8) }
                    ?.let {
                        append(" (")
                        append(it)
                        append(")")
                    }
                append(" by Fantamomo")
            })
            when (unattached) {
                true -> {
                    warn("This plugin is in a unattached state")
                    warn("This means that there have been changes to the code, without a commit")
                    warn("If you are a developer, you can ignore this message.")
                    warn("If you are a server admin, please switch to a official build.")
                    warn("We do not provide support for unattached builds.")
                }
                false -> {}
                else -> {
                    error("The unattached state could not be determined.")
                    error("Please report this issue to the plugin author.")
                }
            }
        }
    }

    override fun onEnable() {
        if (AmongUsConstants.JAR_TYPE == JarType.THIN) {
            // If it is really a thin jar, this code could not be reached
            // because the kotlin runtime is not available,
            // but just in case, the kotlin runtime is available.
            with(slF4JLogger) {
                warn("This Plugin is running the thin version of the jar.")
                warn("Some libraries are not included in the jar.")
                warn("This would lead to exception we could not catch.")
                warn("Please switch to the LITE or the STANDALONE version.")
                warn("The plugin will be disabled.")
            }
            server.pluginManager.disablePlugin(this)
            return
        }
        saveDefaultConfig()
        AmongUsConfig.init()
        if (AmongUsConstants.IN_DEVELOPMENT) with(slF4JLogger) {
            info("This plugin is currently running in development mode.")
            info("Additional development-only features are enabled to assist testing.")
            info("These features may impact performance and must not be used in production.")
            info("They may also expose information that should remain hidden in fair-play environments.")
        }
        LogFilter.init()
        GameAreaManager.loadAreas()
        Listeners.registerAll()
        LanguageManager.init()
        AmongUsSecrets.init()

        initClasses()

        if (AmongUsConstants.IN_DEVELOPMENT) {
            val notSupportBots: MutableList<Role<*, *>> = mutableListOf()
            for (role in Role.roles) runCatching {
                val method = role::class.java.getMethod("assignTo", AmongUsPlayer::class.java)
                val returnType = method.returnType
                if (!SupportBotsRole::class.java.isAssignableFrom(returnType)) notSupportBots.add(role)
            }
            if (notSupportBots.isNotEmpty()) {
                slF4JLogger.warn("The following roles do not have bot support: ${notSupportBots.joinToString { it.id }}")
            }
        }

        AmongUs.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            AmongUsCommands.init(it.registrar())
        }

        Permissions.registerAll()
    }

    @Suppress("UnusedExpression")
    private fun initClasses() {
        ActionLogManager
        AiService
        Role
        Modification
        PlayerDataManager
        BotsJoinMessages
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
        saveRun { LastPlayerLocationManager.save() }
        saveRun { ActionLogManager.saveAll() }
        saveRun { scope.cancel() }

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
