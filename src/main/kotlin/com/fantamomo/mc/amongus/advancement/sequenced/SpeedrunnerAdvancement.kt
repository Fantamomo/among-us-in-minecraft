package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.player.isAlive

@OptIn(ExperimentalAdvancementApi::class)
object SpeedrunnerAdvancement : SequencedAdvancement("speedrunner") {
    override val runner = createRunner {
        step("finish_task") {
            withListener(PlayerKillEvent::class, handler = { if (it.victim == player) fail() }) {
                forbid(MeetingCallEvent::class) {
                    awaitTaskFinished { it.player == player && player.tasks.all { task -> task.completed } }
                }
            }

            if (!player.isAlive()) stepFailed()
        }
    }
}