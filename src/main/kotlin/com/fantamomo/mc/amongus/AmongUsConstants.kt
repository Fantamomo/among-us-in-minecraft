package com.fantamomo.mc.amongus

import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.util.JarType
import org.bukkit.configuration.file.YamlConfiguration
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.util.jar.Manifest
import kotlin.io.path.exists

/**
 * Represents constants and metadata related to the AmongUs plugin.
 *
 * This interface provides access to various configuration values and runtime metadata information
 * required by the application. The constants can either be retrieved from specific configuration
 * files or dynamically calculated at runtime.
 *
 * The values are lazily loaded.
 *
 * @author Fantamomo
 */
@Suppress("PropertyName")
sealed interface AmongUsConstants {
    /**
     * Represents whether the plugin is running in development mode.
     *
     * The development mode is determined by the presence of a debug file in the plugin's data directory.
     */
    val IN_DEVELOPMENT: Boolean

    /**
     * Represents the Git hash of the latest commit, in the environment the plugin was built.
     */
    val GIT_HASH: String?

    /**
     * Represents the type of the plugin's JAR file.
     */
    val JAR_TYPE: JarType

    /**
     * Represents whether the plugin is running in an unattached environment.
     *
     * The plugin is running in an unattached environment if there have been changes to the code without a commit, so that with [GIT_HASH] it is not possible to determine the exact version of the plugin.
     */
    val UNATTACHED: Boolean?

    companion object : AmongUsConstants {
        private var delegate: Impl? = null
        private val impl: Impl
            get() = delegate ?: throw IllegalStateException("AmongUsConstants not initialized")

        override val IN_DEVELOPMENT get() = impl.IN_DEVELOPMENT
        override val GIT_HASH get() = impl.GIT_HASH
        override val JAR_TYPE get() = impl.JAR_TYPE
        override val UNATTACHED get() = impl.UNATTACHED

        internal class Impl(private val dataDirectory: Path) : AmongUsConstants {
            private val paperPluginYml: YamlConfiguration?
            private val manifest: Manifest?

            init {
                if (delegate != null) throw IllegalStateException("AmongUsConstants already initialized")
                delegate = this
                paperPluginYml = try {
                    val url: URL? = this::class.java.getClassLoader().getResource("paper-plugin.yml")

                    if (url != null) {
                        val connection = url.openConnection()
                        connection.setUseCaches(false)
                        connection.getInputStream().reader().use { YamlConfiguration.loadConfiguration(it) }
                    } else null
                } catch (ex: IOException) {
                    null
                }
                manifest = try {
                    val url: URL? = this::class.java.getClassLoader().getResource("META-INF/MANIFEST.MF")
                    if (url != null) {
                        val connection = url.openConnection()
                        connection.setUseCaches(false)
                        connection.getInputStream().use { Manifest(it) }
                    } else null
                } catch (ex: IOException) {
                    null
                }
            }

            override val IN_DEVELOPMENT by lazy { dataDirectory.resolve(AmongUsDebug.DEBUG_FILE_NAME).exists() }
            override val GIT_HASH by lazy { paperPluginYml?.getString("git-hash") }
            override val JAR_TYPE by lazy { manifest?.mainAttributes?.getValue("Jar-Type")?.let(JarType::get) ?: JarType.UNKNOWN }
            override val UNATTACHED by lazy { paperPluginYml?.getBoolean("unattached") }
        }
    }
}