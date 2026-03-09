package com.fantamomo.mc.amongus

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import org.bukkit.configuration.file.YamlConfiguration
import java.io.IOException
import java.net.URL
import kotlin.io.path.exists

@Suppress("PropertyName")
sealed interface AmongUsConstants {
    val IN_DEVELOPMENT: Boolean
    val GIT_HASH: String?

    companion object : AmongUsConstants {
        private var delegate: Impl? = null
        private val impl: Impl
            get() = delegate ?: throw IllegalStateException("AmongUsConstants not initialized")

        override val IN_DEVELOPMENT get() = impl.IN_DEVELOPMENT
        override val GIT_HASH get() = impl.GIT_HASH

        @Suppress("UnstableApiUsage")
        internal class Impl(private val context: BootstrapContext) : AmongUsConstants {
            private val paperPluginYml: YamlConfiguration?

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
            }

            override val IN_DEVELOPMENT by lazy { context.dataDirectory.resolve("IN_DEVELOPMENT").exists() }
            override val GIT_HASH by lazy { paperPluginYml?.getString("git-hash") }
        }
    }
}