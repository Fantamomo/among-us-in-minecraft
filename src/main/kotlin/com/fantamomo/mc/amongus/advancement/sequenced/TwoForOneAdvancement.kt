package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.resultWithoutVotesOf
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.advancement.utils.voteTargetOf
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.MayorRole
import org.bukkit.inventory.ItemType

/**
 * As the Mayor, give both your votes to the same player, they get ejected, and without your votes they would not have been.
 */
@OptIn(ExperimentalAdvancementApi::class)
object TwoForOneAdvancement : SequencedAdvancement("two_for_one") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.GOLDEN_HELMET

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == MayorRole

    override val runner = createRunner {
        step("meeting_start") { awaitMeetingCall() }
        val meetingEnd = step("meeting_end") { awaitMeetingEnd() }

        val target = step("double_down") {
            val normalVote = meetingEnd.voteTargetOf(player)
            val mayorVote = meetingEnd.voteTargetOf(player, mayorVote = true)
            if (normalVote == null || normalVote != mayorVote) stepFailed()
            normalVote
        }

        step("power_of_democracy") {
            if (meetingEnd.ejectedPlayer != target) stepFailed()
        }

        step("tie_breaker") {
            if (meetingEnd.resultWithoutVotesOf(player) == target) stepFailed()
        }
    }
}
