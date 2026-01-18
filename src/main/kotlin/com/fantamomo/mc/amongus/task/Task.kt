package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.tasks.*

interface Task<T : Task<T, A>, A : AssignedTask<T, A>> {
    val id: String
    val type: TaskType

    fun isAvailable(game: Game): Boolean

    fun assignTo(player: AmongUsPlayer): A

    companion object {
        val tasks = setOf<Task<*, *>>(
            GarbageTask,
            NumbersTask,
            StartReaktorTask,
            FixWireTask,
            SwipeCardTask,
            InspectSampleTask,
            InsertKeyTask,
            ScanBoardingPassTask,
            TransferDataTask,
            FixWeatherNodeTask,
            RepairDrillTask
        )
    }
}