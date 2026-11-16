@file:OptIn(ExperimentalAdvancementApi::class)

package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.events.vents.VentEnterEvent
import com.fantamomo.mc.amongus.events.vents.VentExitEvent


suspend fun AdvancementSequence.awaitEnterVent(): VentEnterEvent = awaitEvent()

suspend fun AdvancementSequence.awaitExitVent(): VentExitEvent = awaitEvent()

suspend fun AdvancementSequence.awaitKill(): PlayerKillEvent = awaitEvent()

suspend fun AdvancementSequence.awaitMeetingCall(): MeetingCallEvent = awaitEvent()

suspend fun AdvancementSequence.awaitMeetingEnd(): MeetingEndEvent = awaitEvent()