package com.fantamomo.mc.amongus.util

import com.fantamomo.mc.amongus.AmongUs
import java.lang.ref.WeakReference

object CooldownManager {

    private val cooldowns: MutableList<WeakReference<Cooldown>> = mutableListOf()
    private var taskId = -1

    fun init() {
        if (taskId != -1) return
        taskId = AmongUs.server.scheduler.scheduleSyncRepeatingTask(AmongUs, ::tick, 0L, 1L)
    }

    internal fun register(cooldown: Cooldown) {
        init()
        cooldowns.add(WeakReference(cooldown))
    }

    private fun tick() {
        cleanup()
        cooldowns.forEach { it.get()?.update() }
    }

    private fun cleanup() {
        cooldowns.removeIf { it.get() == null }
        if (cooldowns.isEmpty() && taskId != -1) {
            AmongUs.server.scheduler.cancelTask(taskId)
            taskId = -1
        }
    }
}
