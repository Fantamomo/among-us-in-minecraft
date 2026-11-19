package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team

@OptIn(ExperimentalAdvancementApi::class)
object DramaticExitAdvancement : SequencedAdvancement("dramatic_exit") {

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition.team == Team.CREWMATES

    override val runner = createRunner {
        step("meeting_call") {
            awaitOwnPlayer { awaitMeetingCall() }
        }
        val meetingEnd = step("meeting_end") {
            awaitMeetingEnd()
        }
        step("dramatic_exit") {
            if (meetingEnd.ejectedPlayer != player) stepFailed()
        }

        award()
    }
}