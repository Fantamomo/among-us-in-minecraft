package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.ExperimentalAdvancementApi
import com.fantamomo.mc.amongus.advancement.SequencedAdvancement
import com.fantamomo.mc.amongus.advancement.awaitCustomLogic
import com.fantamomo.mc.amongus.advancement.createRunner
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.events.player.TaskFinishedEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.SnitchRole

@OptIn(ExperimentalAdvancementApi::class)
object TheSnitchWhoSnitchedTooLateAdvancement : SequencedAdvancement("the_snitch_who_snitched_too_late") {
    override val isSecret = true

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == SnitchRole

    override val runner = createRunner {
        val alreadyCompleted = player.tasks.any { !it.completed }
        if (alreadyCompleted) awaitInfinity()
        step("finish_tasks") {
            awaitCustomLogic {
                while (player.tasks.any { !it.completed }) {
                    awaitEvent<TaskFinishedEvent>()
                }
            }
        }

        step("ignorance_is_bliss") {
            awaitCustomLogic {
                while (true) {
                    when (val event = awaitEvents(PlayerKillEvent::class, MeetingCallEvent::class)) {
                        is MeetingCallEvent -> stepFailed()
                        is PlayerKillEvent -> {
                            if (event.victim == player) break
                        }
                    }
                }
            }
        }

        award()
    }
}