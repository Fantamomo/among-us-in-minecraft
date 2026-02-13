package com.fantamomo.mc.amongus

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext

/**
 * This class initializes and provides the entry point for the AmongUs plugin's lifecycle.
 *
 * @author Fantamomo
 * @since 1.0-SNAPSHOT
 */
@Suppress("UnstableApiUsage")
class AmongUsBootstrapper : PluginBootstrap {
    override fun bootstrap(p0: BootstrapContext) {

    }

    /**
     * We only override this method to provide the [AmongUs] `object` instance.
     * Paper does not support kotlin `object`s.
     */
    override fun createPlugin(context: PluginProviderContext) = AmongUs
}