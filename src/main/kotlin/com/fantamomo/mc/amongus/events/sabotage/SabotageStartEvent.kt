package com.fantamomo.mc.amongus.events.sabotage

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.Sabotage

class SabotageStartEvent(val sabotage: Sabotage<*, *>, val player: AmongUsPlayer?, val startedAt: Long) : AmongUsEvent