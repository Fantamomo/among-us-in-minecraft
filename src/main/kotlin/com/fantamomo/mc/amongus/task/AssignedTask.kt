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

    /**
     * Retrieves the current state of the assigned task.
     *
     * If the state is [TaskState.COMMUNICATIONS_SABOTAGED], it is interpreted as `null`.
     *
     * @return the current state of the task, or `null` if there is no specific state, allowing the [TaskManager] to determine it.
     * If the task has invoked [TaskManager.completeTask], the state will be [TaskState.COMPLETED].
     * For tasks implementing [Steppable], the state is determined using the [Steppable.step] and [Steppable.maxSteps] properties.
     */
    fun state(): TaskState? = null
}