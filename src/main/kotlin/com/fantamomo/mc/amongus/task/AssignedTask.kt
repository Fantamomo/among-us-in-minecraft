package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component
import org.bukkit.Location

interface AssignedTask<T : Task<T, A>, A : AssignedTask<T, A>> {
    val task: T
    val player: AmongUsPlayer

    val location: Location

    fun start()

    fun stop()

    fun tick() {}

    fun scoreboardLine(): Component = Component.translatable(task.title)

    fun state(): TaskState? = null
}