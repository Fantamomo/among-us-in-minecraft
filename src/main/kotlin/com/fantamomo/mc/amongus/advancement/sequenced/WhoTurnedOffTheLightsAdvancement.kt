package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.sabotage.SabotageEndEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.sabotage.SabotageType
import org.bukkit.inventory.ItemType

/**
 * Sabotage the lights, kill two players while they are off, and then help to fix them.
 */
@OptIn(ExperimentalAdvancementApi::class)
object WhoTurnedOffTheLightsAdvancement : SequencedAdvancement("who_turned_off_the_lights") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.REDSTONE_LAMP

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition.team === Team.IMPOSTERS

    override val runner = createRunner {
        val sabotage = step("sabotage_lights") {
            awaitSabotageStart { it.player == player && it.sabotage.sabotageType == SabotageType.Lights }
        }

        // both kills must happen while the lights are still off
        forbid(SabotageEndEvent::class, { it.startedAt == sabotage.startedAt }) {
            step("first_victim") { awaitOwnKill() }
            step("second_victim") { awaitOwnKill() }
        }

        step("electrician_of_the_year") {
            // flip a lever back on before the sabotage is fixed ...
            forbid(SabotageEndEvent::class) {
                awaitLightLeverFlip { it.player == player && it.isOn }
            }
            // ... and the sabotage gets fixed
            val end = awaitSabotageEnd()
            if (end.startedAt != sabotage.startedAt) stepFailed()
        }
    }
}
