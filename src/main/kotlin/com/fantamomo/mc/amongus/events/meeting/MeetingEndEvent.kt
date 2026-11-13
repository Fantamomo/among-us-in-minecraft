package com.fantamomo.mc.amongus.events.meeting

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class MeetingEndEvent(
    val ejectedPlayer: AmongUsPlayer?
) : AmongUsEvent