package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.inventory.ItemType

/**
 * Kill three players without a single meeting being called in between.
 *
 * Example for a guard ([forbid]): the meeting call fails the attempt, no matter which kill it is waiting for.
 */
@OptIn(ExperimentalAdvancementApi::class)
object CleanUpOnAisle3Advancement : SequencedAdvancement("clean_up_on_aisle_3") {
    override val hasGameAdvantage = true

    @Suppress("UnstableApiUsage")
    override val icon: ItemType get() = ItemType.SPONGE

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition.team === Team.IMPOSTERS

    override val runner = createRunner {
        step("first") { awaitOwnKill() }
        forbid(MeetingCallEvent::class) {
            step("second") { awaitOwnKill() }
            step("third") { awaitOwnKill() }
        }
    }
}
