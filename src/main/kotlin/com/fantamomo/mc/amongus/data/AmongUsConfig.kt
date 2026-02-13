package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs

object AmongUsConfig {
    private val config = AmongUs.config

    open class ConfigSection(val sectionId: String) {
        val section = config.getConfigurationSection(sectionId) ?: config.createSection(sectionId)
    }

    object MsgCommandBlocker : ConfigSection("msg-command-blocker") {
        val disabled = section.getBoolean("disabled", false)
        val legacy = section.getBoolean("legacy", false)
        val commands = section.getStringList("commands")
    }
}