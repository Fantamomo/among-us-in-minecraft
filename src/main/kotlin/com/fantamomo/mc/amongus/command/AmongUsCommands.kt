package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.amongus.AmongUs
import io.papermc.paper.command.brigadier.Commands

object AmongUsCommands {
    fun registerAll(registrar: Commands) {
        registrar.register(AmongUs.pluginMeta, AmongUsAdminCommand, "Among Us Admin Command", listOf("aua"))
    }
}