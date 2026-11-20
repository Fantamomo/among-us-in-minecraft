package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.role.neutral.JesterRole
import org.bukkit.inventory.ItemType

/**
 * As the Jester, get ejected with every other living player voting for you.
 */
@OptIn(ExperimentalAdvancementApi::class)
object StandingOvationAdvancement : SequencedAdvancement("standing_ovation") {
    override val isSecret = true

    @Suppress("UnstableApiUsage")
    override val icon: ItemType get() = ItemType.JUKEBOX

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
            val otherVotes = meetingEnd.votes.filterKeys { it.player != player }
            val allVotesForPlayer = otherVotes.values.all { (it as? MeetingManager.Vote.For)?.target == player }
            if (!allVotesForPlayer) stepFailed()
            // the meeting end event is called before the ejection, so the player is still alive here
            val voters = otherVotes.keys.mapTo(mutableSetOf()) { it.player }
            val everyoneVoted = player.game.players.all { it == player || !it.isAlive() || it in voters }
            if (!everyoneVoted) stepFailed()
        }
    }
}
