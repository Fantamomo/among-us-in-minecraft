package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.manager.MeetingManager.Vote
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.neutral.ExecutionerRole

@OptIn(ExperimentalAdvancementApi::class)
object HiredGunFiredGunAdvancement : SequencedAdvancement("hired_gun_fired_gun") {

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == ExecutionerRole

    override val runner = createRunner {
        step("meeting") { awaitMeetingCall() }
        val executionerRole = player.role as? ExecutionerRole.AssignedExecutionerRole ?: throw IllegalStateException("Player is not an executioner")
        val meetingEnd = step("cast_the_stone") {
            val event = awaitMeetingEnd()
            val executionerTarget = executionerRole.target
            val voteTarget = (event.votes.entries.find { it.key.player == player }?.value as? Vote.For)?.target
            if (voteTarget != executionerTarget) {
                stepFailed()
            }
            event
        }

        step("contract_fulfilled") {
            if (executionerRole.target !== meetingEnd.ejectedPlayer) {
                stepFailed()
            }
        }

        step("deciding_vote") {
            val ejectedPlayerWithoutExecutioner = calculateResult(meetingEnd)
            if (ejectedPlayerWithoutExecutioner === meetingEnd.ejectedPlayer) {
                stepFailed()
            }
        }

        award()
    }

    private fun AdvancementSequence.calculateResult(meetingEnd: MeetingEndEvent): AmongUsPlayer? {
        val counts = mutableMapOf<AmongUsPlayer, Int>()
        var skipVotes = 0

        meetingEnd.votes.filter { it.key.player != player }.values.forEach { vote ->
            when (vote) {
                Vote.Skip -> skipVotes++
                is Vote.For -> counts[vote.target] = (counts[vote.target] ?: 0) + 1
            }
        }

        val highest = counts.maxByOrNull { it.value } ?: return null
        if (highest.value <= skipVotes) return null
        val sameVotes = counts.values.count { it == highest.value }

        return if (sameVotes == 1) highest.key else null
    }
}