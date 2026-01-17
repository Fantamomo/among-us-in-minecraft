package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.tasks.FixWireTask
import com.fantamomo.mc.amongus.task.tasks.GarbageTask
import com.fantamomo.mc.amongus.task.tasks.NumbersTask
import com.fantamomo.mc.amongus.task.tasks.StartReaktorTask

interface Task<T : Task<T, A>, A : AssignedTask<T, A>> {
    val id: String
    val type: TaskType

    fun assignTo(player: AmongUsPlayer): A?

    companion object {
        val tasks = setOf<Task<*, *>>(
            GarbageTask,
            NumbersTask,
            StartReaktorTask,
            FixWireTask
        )
    }
}