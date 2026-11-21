package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.ExperimentalAdvancementApi
import com.fantamomo.mc.amongus.advancement.SequencedAdvancement
import com.fantamomo.mc.amongus.advancement.awaitMeetingEnd
import com.fantamomo.mc.amongus.advancement.createRunner
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.inventory.ItemType

@OptIn(ExperimentalAdvancementApi::class)
object WrongPlaceWrongTimeAdvancement : SequencedAdvancement("wrong_place_wrong_time") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.CLOCK
    override val isSecret: Boolean = true

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition.team == Team.CREWMATES

    override val runner = createRunner {
        val meetingEnd = step("first_meeting_end") {
            val result = awaitMeetingEnd()
            if (result.meeting.number != 0) stepFailed()
            result
        }

        step("ejected") {
            if (meetingEnd.ejectedPlayer != player) stepFailed()
        }
    }
}