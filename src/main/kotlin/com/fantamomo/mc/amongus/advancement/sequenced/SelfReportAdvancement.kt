package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

/**
 * The sequence of events for the self report:
 * 1. Kill someone
 * 2. Report that body yourself within 10 seconds
 * 3. Survive the following meeting
 */
@OptIn(ExperimentalAdvancementApi::class)
object SelfReportAdvancement : SequencedAdvancement("self_report") {

    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.BELL

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition.team == Team.IMPOSTERS

    override val runner = createRunner {
        val killEvent = step("kill") {
            awaitOwnPlayer {
                awaitKill()
            }
        }

        step("meeting_call") {
            val meetingCall = withTimeout(10.seconds) {
                awaitMeetingCall()
            }
            if (meetingCall.player != player || meetingCall.body != killEvent.victim) stepFailed()
        }

        step("meeting_end") {
            val meetingEnd = awaitMeetingEnd()
            if (meetingEnd.ejectedPlayer == player) stepFailed()
        }

        award()
    }
}
