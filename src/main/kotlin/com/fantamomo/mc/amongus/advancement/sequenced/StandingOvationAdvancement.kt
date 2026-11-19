package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.role.neutral.JesterRole

@OptIn(ExperimentalAdvancementApi::class)
object StandingOvationAdvancement : SequencedAdvancement("standing_ovation") {
    override val isSecret = true

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == JesterRole

    override val runner = createRunner {
        step("meeting") {
            awaitMeetingCall()
            if (!player.isAlive()) stepFailed()
        }
        val meetingEnd = step("ejected") {
            val end = awaitMeetingEnd()
            if (end.ejectedPlayer != player) stepFailed()
            end
        }
        step("everybody_hates_you") {
            val votes = meetingEnd.votes
            val allVotesMatchPlayer = votes.all { it.key == player || (it.value as? MeetingManager.Vote.For)?.target == player }
            if (!allVotesMatchPlayer) stepFailed()
            val everyoneVoted = votes.keys.map { it.player }.containsAll(player.game.players.filter { it.isAlive() })
            if (!everyoneVoted) stepFailed()
        }
    }
}