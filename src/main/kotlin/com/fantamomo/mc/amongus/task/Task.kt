package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.tasks.GarbageTask

interface Task<T : Task<T, A>, A : AssignedTask<T, A>> {
    val id: String
    val type: TaskType
    val common: Boolean
        get() = false

    fun assignTo(player: AmongUsPlayer): A?

    companion object {
        val tasks = setOf<Task<*, *>>(
            GarbageTask
        )
    }
}