package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.sabotage.LightLeverFlipEvent
import com.fantamomo.mc.amongus.events.sabotage.SabotageEndEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.sabotage.SabotageType

@OptIn(ExperimentalAdvancementApi::class)
object WhoTurnedOffTheLightsAdvancement : SequencedAdvancement("who_turned_off_the_lights") {
    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition.team === Team.IMPOSTERS

    override val runner = createRunner {
        val lightsSabotage = step("sabotage_lights") {
            awaitCustomLogicFilter(AdvancementSequence::awaitSabotageStart) { event ->
                event.player == player && event.sabotage.sabotageType == SabotageType.Lights
            }
        }
        val lightsSabotageStartedAt = lightsSabotage.startedAt
        step("first_victim") { awaitOwnPlayer { awaitKill() } }
        step("second_victim") { awaitOwnPlayer { awaitKill() } }
        step("electrician_of_the_year") {
            awaitCustomLogicFilter(
                { awaitOwnPlayer { awaitEvent<LightLeverFlipEvent>() } }
            ) { it.isOn }
            val endEvent = awaitEvent<SabotageEndEvent>()
            if (endEvent.startedAt != lightsSabotageStartedAt) stepFailed()
        }

        award()
    }
}