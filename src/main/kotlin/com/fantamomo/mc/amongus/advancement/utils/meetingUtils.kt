package com.fantamomo.mc.amongus.advancement.utils

import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.manager.MeetingManager.Vote
import com.fantamomo.mc.amongus.manager.MeetingManager.Voter
import com.fantamomo.mc.amongus.player.AmongUsPlayer

/**
 * The player [voter] voted for with their normal vote (or their mayor vote if [mayorVote]), `null` for skip or no vote.
 */
fun MeetingEndEvent.voteTargetOf(voter: AmongUsPlayer, mayorVote: Boolean = false): AmongUsPlayer? {
    val entry = votes.entries.find {
        it.key.player == voter && (if (mayorVote) it.key is Voter.MayorVoter else it.key is Voter.NormalPlayer)
    }
    return (entry?.value as? Vote.For)?.target
}

/** All players who voted for [target] (a mayor is listed once per vote). */
fun MeetingEndEvent.votersFor(target: AmongUsPlayer): List<AmongUsPlayer> =
    votes.entries.filter { (it.value as? Vote.For)?.target == target }.map { it.key.player }

/**
 * The player that would have been ejected if all votes of [player] were missing,
 * using the same rules as the meeting (a tie or more skip votes eject nobody).
 */
fun MeetingEndEvent.resultWithoutVotesOf(player: AmongUsPlayer): AmongUsPlayer? =
    calculateVoteResult(votes.filterKeys { it.player != player }.values)

internal fun calculateVoteResult(votes: Collection<Vote>): AmongUsPlayer? {
    val counts = mutableMapOf<AmongUsPlayer, Int>()
    var skipVotes = 0

    for (vote in votes) {
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
