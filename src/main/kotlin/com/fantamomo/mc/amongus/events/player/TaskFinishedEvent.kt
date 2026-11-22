package com.fantamomo.mc.amongus.events.player

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.AssignedTask

class TaskFinishedEvent(val task: AssignedTask<*, *>) : AmongUsEvent, PlayerAmongUsEvent {
    override val player: AmongUsPlayer get() = task.player
}