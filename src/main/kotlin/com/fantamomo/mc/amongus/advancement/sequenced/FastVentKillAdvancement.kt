package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.events.vents.VentEnterEvent
import com.fantamomo.mc.amongus.events.vents.VentExitEvent
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

/**
 * The sequence of events for a fast vent kill:
 * 1. Player exits vent
 * 2. Player kills victim
 * 3. Player enters the same vent again
 * (2 and 3 done in under 10 seconds)
 */
@OptIn(ExperimentalAdvancementApi::class)
data object FastVentKillAdvancement : SequencedAdvancement("fast_vent_kill") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.IRON_TRAPDOOR

    override val runner = createRunner {
        val exit = step("exit_vent") {
            awaitOwn<VentExitEvent>()
        }
        withTimeout(10.seconds) {
            step("kill") {
                awaitOwnKill()
            }
            step("enter_vent") {
                awaitOwn<VentEnterEvent> { it.vent.uuid == exit.vent.uuid }
            }
        }
    }
}
