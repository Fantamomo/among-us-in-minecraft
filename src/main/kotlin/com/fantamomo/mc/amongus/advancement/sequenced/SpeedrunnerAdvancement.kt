package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.meeting.MeetingCallEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.canDoTasks
import com.fantamomo.mc.amongus.player.isAlive
import org.bukkit.inventory.ItemType

@OptIn(ExperimentalAdvancementApi::class)
object SpeedrunnerAdvancement : SequencedAdvancement("speedrunner") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.RABBIT_FOOT

    override fun applicableTo(player: HumanAmongUsPlayer) = player.canDoTasks()

    override val runner = createRunner {
        step("finish_task") {
            withListener(PlayerKillEvent::class, handler = { if (it.victim == player) fail() }) {
                forbid(MeetingCallEvent::class) {
                    awaitTaskFinished { it.player == player && player.tasks.all { task -> task.completed } }
                }
            }

            if (!player.isAlive()) stepFailed()
        }
    }
}