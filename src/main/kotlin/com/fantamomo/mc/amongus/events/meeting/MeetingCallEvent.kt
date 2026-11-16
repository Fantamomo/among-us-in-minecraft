package com.fantamomo.mc.amongus.events.meeting

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class MeetingCallEvent(
    override val player: AmongUsPlayer,
    val reason: MeetingManager.MeetingReason,
    val body: AmongUsPlayer?
) : AmongUsEvent, PlayerAmongUsEvent