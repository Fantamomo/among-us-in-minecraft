package com.fantamomo.mc.amongus.listeners

import com.fantamomo.mc.amongus.AmongUs
import org.bukkit.event.Listener

object Listeners {
    private val listeners: MutableList<Listener> = mutableListOf(
        AbilityListener,
        CameraListener,
        ChatListener,
        MeetingListener,
        PlayerListener,
        SabotageListener,
        SettingsListener,
        TaskListener,
        VentListener
    )

    fun registerAll() {
        listeners.forEach {
            AmongUs.server.pluginManager.registerEvents(it, AmongUs)
        }
    }
}