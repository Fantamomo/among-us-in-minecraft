package com.fantamomo.mc.amongus

import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.util.JarType
import org.bukkit.configuration.file.YamlConfiguration
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.util.jar.Manifest
import kotlin.io.path.exists

@Suppress("PropertyName")
sealed interface AmongUsConstants {
    val IN_DEVELOPMENT: Boolean
    val GIT_HASH: String?
    val JAR_TYPE: JarType

    companion object : AmongUsConstants {
        private var delegate: Impl? = null
        private val impl: Impl
            get() = delegate ?: throw IllegalStateException("AmongUsConstants not initialized")

        override val IN_DEVELOPMENT get() = impl.IN_DEVELOPMENT
        override val GIT_HASH get() = impl.GIT_HASH
        override val JAR_TYPE get() = impl.JAR_TYPE

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
            override val JAR_TYPE: JarType by lazy { manifest?.mainAttributes?.getValue("Jar-Type")?.let(JarType::get) ?: JarType.UNKNOWN }
        }
    }
}