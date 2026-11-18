package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

/**
 * The sequence of events for a fast vent kill:
 * 1. Player exits vent
 * 2. Player kills victim
 * 3. Player enters vent
 * (all done in under 10 seconds)
 */
@OptIn(ExperimentalAdvancementApi::class)
data object FastVentKillAdvancement : SequencedAdvancement("fast_vent_kill") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.IRON_TRAPDOOR

    override val runner = createRunner {
        step("exit_vent") {
            awaitOwnPlayer { awaitExitVent() }
        }
        withTimeout(10.seconds) {
            step("kill") {
                awaitOwnPlayer { awaitKill() }
            }
            step("enter_vent") {
                awaitOwnPlayer { awaitEnterVent() }
            }
        }
        award()
    }
}