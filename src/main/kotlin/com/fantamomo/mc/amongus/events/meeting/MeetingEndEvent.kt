package com.fantamomo.mc.amongus.events.meeting

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class MeetingEndEvent(
    val ejectedPlayer: AmongUsPlayer?,
    val votes: Map<MeetingManager.Voter, MeetingManager.Vote>,
    val meeting: MeetingManager.Meeting
) : AmongUsEvent