package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.resultWithoutVotesOf
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.advancement.utils.voteTargetOf
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.neutral.ExecutionerRole
import org.bukkit.inventory.ItemType

/**
 * Your target gets ejected, and your vote was the deciding one.
 */
@OptIn(ExperimentalAdvancementApi::class)
object HiredGunFiredGunAdvancement : SequencedAdvancement("hired_gun_fired_gun") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.CROSSBOW

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == ExecutionerRole

    override val runner = createRunner {
        step("meeting") { awaitMeetingCall() }
        val executionerRole = player.role as? ExecutionerRole.AssignedExecutionerRole ?: fail("Player is not an executioner")
        val meetingEnd = step("cast_the_stone") {
            val event = awaitMeetingEnd()
            if (event.voteTargetOf(player) != executionerRole.target) stepFailed()
            event
        }

        step("contract_fulfilled") {
            if (executionerRole.target !== meetingEnd.ejectedPlayer) stepFailed()
        }

        step("deciding_vote") {
            if (meetingEnd.resultWithoutVotesOf(player) === meetingEnd.ejectedPlayer) stepFailed()
        }
    }
}
