package com.fantamomo.mc.amongus

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext

@Suppress("UnstableApiUsage")
class AmongUsBootstrapper : PluginBootstrap {
    override fun bootstrap(p0: BootstrapContext) {

    }

    override fun createPlugin(context: PluginProviderContext) = AmongUs
}