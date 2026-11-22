@file:OptIn(ExperimentalAdvancementApi::class)

package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.events.player.TaskFinishedEvent
import com.fantamomo.mc.amongus.events.roles.MorphStartEvent
import com.fantamomo.mc.amongus.events.roles.MorphStopEvent
import com.fantamomo.mc.amongus.events.sabotage.LightLeverFlipEvent
import com.fantamomo.mc.amongus.events.sabotage.SabotageEndEvent
import com.fantamomo.mc.amongus.events.sabotage.SabotageStartEvent
import com.fantamomo.mc.amongus.events.vents.VentEnterEvent
import com.fantamomo.mc.amongus.events.vents.VentExitEvent

/*
 * Typed shortcuts for AdvancementSequence.awaitEvent. Every function takes an optional filter,
 * events that don't match are ignored.
 */

suspend fun AdvancementSequence.awaitEnterVent(filter: (VentEnterEvent) -> Boolean = { true }): VentEnterEvent =
    awaitEvent(VentEnterEvent::class.java, filter)

suspend fun AdvancementSequence.awaitExitVent(filter: (VentExitEvent) -> Boolean = { true }): VentExitEvent =
    awaitEvent(VentExitEvent::class.java, filter)

suspend fun AdvancementSequence.awaitKill(filter: (PlayerKillEvent) -> Boolean = { true }): PlayerKillEvent =
    awaitEvent(PlayerKillEvent::class.java, filter)

/** Waits until the owner of the sequence kills someone. */
suspend fun AdvancementSequence.awaitOwnKill(filter: (PlayerKillEvent) -> Boolean = { true }): PlayerKillEvent =
    awaitKill { it.player == player && filter(it) }

/** Waits until the owner of the sequence gets killed. */
suspend fun AdvancementSequence.awaitKilled(filter: (PlayerKillEvent) -> Boolean = { true }): PlayerKillEvent =
    awaitKill { it.victim == player && filter(it) }

suspend fun AdvancementSequence.awaitMeetingCall(filter: (MeetingCallEvent) -> Boolean = { true }): MeetingCallEvent =
    awaitEvent(MeetingCallEvent::class.java, filter)

suspend fun AdvancementSequence.awaitMeetingEnd(filter: (MeetingEndEvent) -> Boolean = { true }): MeetingEndEvent =
    awaitEvent(MeetingEndEvent::class.java, filter)

suspend fun AdvancementSequence.awaitMorphStart(filter: (MorphStartEvent) -> Boolean = { true }): MorphStartEvent =
    awaitEvent(MorphStartEvent::class.java, filter)

suspend fun AdvancementSequence.awaitMorphStop(filter: (MorphStopEvent) -> Boolean = { true }): MorphStopEvent =
    awaitEvent(MorphStopEvent::class.java, filter)

suspend fun AdvancementSequence.awaitSabotageStart(filter: (SabotageStartEvent) -> Boolean = { true }): SabotageStartEvent =
    awaitEvent(SabotageStartEvent::class.java, filter)

suspend fun AdvancementSequence.awaitSabotageEnd(filter: (SabotageEndEvent) -> Boolean = { true }): SabotageEndEvent =
    awaitEvent(SabotageEndEvent::class.java, filter)

suspend fun AdvancementSequence.awaitLightLeverFlip(filter: (LightLeverFlipEvent) -> Boolean = { true }): LightLeverFlipEvent =
    awaitEvent(LightLeverFlipEvent::class.java, filter)

suspend fun AdvancementSequence.awaitTaskFinished(filter: (TaskFinishedEvent) -> Boolean = { true }): TaskFinishedEvent =
    awaitEvent(TaskFinishedEvent::class.java, filter)
