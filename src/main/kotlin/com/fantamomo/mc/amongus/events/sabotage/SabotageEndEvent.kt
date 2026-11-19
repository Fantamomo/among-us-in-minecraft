package com.fantamomo.mc.amongus.events.sabotage

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.sabotage.Sabotage

class SabotageEndEvent(val sabotage: Sabotage<*, *>, val startedAt: Long) : AmongUsEvent