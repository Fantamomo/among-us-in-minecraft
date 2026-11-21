package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.advancement.utils.witnessesOf
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.inventory.ItemType

/**
 * The sequence of events for the perfect kill:
 * 1. Kill someone, but with two witnesses
 * 2. one witness reports the body within 15 seconds
 * 3. The killer survives the following meeting
 */
@OptIn(ExperimentalAdvancementApi::class)
object ThePerfectKillAdvancement : SequencedAdvancement("the_perfect_kill") {

    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.IRON_SWORD

    override fun applicableTo(player: HumanAmongUsPlayer) =
        player.role.definition.team == Team.IMPOSTERS

    override val runner = createRunner {
        val killEvent = step("kill") {
            awaitOwnKill()
        }

        // at least two players (besides the killer) must have seen the victim
        val witnesses = step("at_least_two_witnesses") {
            val witnesses = witnessesOf(killEvent.victim, exclude = listOf(player))
            if (witnesses.size < 2) stepFailed()
            witnesses
        }

        // a witness must report the body within 15 seconds
        step("witness_meeting_call", timeoutSeconds = 15) {
            val meetingCall = awaitMeetingCall()
            if (meetingCall.player !in witnesses) stepFailed()
        }

        // the killer must survive the meeting
        step("survive_meeting") {
            val meetingEnd = awaitMeetingEnd()
            if (meetingEnd.ejectedPlayer == player) stepFailed()
        }
    }
}
