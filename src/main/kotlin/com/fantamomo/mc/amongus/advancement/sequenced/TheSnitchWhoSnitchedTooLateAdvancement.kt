package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.role.crewmates.SnitchRole
import org.bukkit.inventory.ItemType

/**
 * As the Snitch, finish all your tasks (and find out who the imposters are), but get killed before the next meeting.
 */
@OptIn(ExperimentalAdvancementApi::class)
object TheSnitchWhoSnitchedTooLateAdvancement : SequencedAdvancement("the_snitch_who_snitched_too_late") {
    override val isSecret = true

    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.SPYGLASS

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == SnitchRole

    override val runner = createRunner {
        step("finish_tasks") {
            withListener(MeetingEndEvent::class, handler = { if (it.ejectedPlayer == player) fail() }) {
                withListener(PlayerKillEvent::class, handler = { if (it.victim == player) fail() }) {
                    // the event is called after the task is marked as completed
                    awaitTaskFinished { it.player == player && player.tasks.all { task -> task.completed } }
                }
            }
            if (!player.isAlive()) stepFailed()
        }

        step("ignorance_is_bliss") {
            forbid(MeetingCallEvent::class) {
                awaitKilled()
            }
        }
    }
}
