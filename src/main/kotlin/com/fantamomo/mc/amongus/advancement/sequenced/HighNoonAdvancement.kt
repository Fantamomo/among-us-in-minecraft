package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.crewmates.SheriffRole
import org.bukkit.inventory.ItemType

/**
 * As the Sheriff, see a kill happen and shoot the killer within 5 seconds, before anyone calls a meeting.
 *
 * Example for a step timeout combined with a guard.
 */
@OptIn(ExperimentalAdvancementApi::class)
object HighNoonAdvancement : SequencedAdvancement("high_noon") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType get() = ItemType.BOW

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == SheriffRole

    override val runner = createRunner {
        val crime = step("witness") {
            awaitKill { it.player != player && player.canSee(it.victim) }
        }
        step("draw", timeoutSeconds = 5) {
            forbid(MeetingCallEvent::class) {
                awaitOwnKill { it.victim == crime.player }
            }
        }
    }
}
