package com.fantamomo.mc.amongus.task

import org.bukkit.Location

val AssignedTask<*, *>.areaLocation: Location?
    get() = player.game.area.tasks[task.id]?.firstOrNull()