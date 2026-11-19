package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.manager.MeetingManager.Vote
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.MayorRole

@OptIn(ExperimentalAdvancementApi::class)
object TwoForOneAdvancement : SequencedAdvancement("two_for_one") {

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == MayorRole

    override val runner = createRunner {
        step("meeting_start") { awaitMeetingCall() }
        val meetingEnd = step("meeting_end") { awaitMeetingEnd() }

        val mayorRole =
            player.role as? MayorRole.AssignedMayorRole ?: throw IllegalStateException("Player is not a mayor")

        val target = step("double_down") {
            val normalVote =
                meetingEnd.votes.entries.find { it.key is MeetingManager.Voter.NormalPlayer && it.key.player == player }?.value as? MeetingManager.Vote.For
            val mayorVote =
                meetingEnd.votes.entries.find { it.key is MeetingManager.Voter.MayorVoter && it.key.player == player }?.value as? MeetingManager.Vote.For

            if (normalVote == null || normalVote != mayorVote) stepFailed()
            normalVote
        }

        step("tie_breaker") {
            val ejectedWithoutMayorVote = calculateResult(meetingEnd)
            if (ejectedWithoutMayorVote != target) stepFailed()
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