package com.fantamomo.mc.amongus

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import kotlin.io.path.exists

@Suppress("UnstableApiUsage", "PropertyName")
sealed interface AmongUsConstants {
    val IN_DEVELOPMENT: Boolean

    companion object : AmongUsConstants {
        private var delegate: Impl? = null
        private val impl: Impl
            get() = delegate ?: throw IllegalStateException("AmongUsConstants not initialized")

        override val IN_DEVELOPMENT get() = impl.IN_DEVELOPMENT

        internal class Impl(private val context: BootstrapContext) : AmongUsConstants {
            init {
                if (delegate != null) throw IllegalStateException("AmongUsConstants already initialized")
                delegate = this
            }

            override val IN_DEVELOPMENT by lazy { context.dataDirectory.resolve("IN_DEVELOPMENT").exists() }
        }
    }
}