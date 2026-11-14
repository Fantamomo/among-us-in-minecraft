package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
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
    override val runner = createRunner {
        awaitOwnPlayer { awaitExitVent() }
        withTimeout(10.seconds) {
            awaitOwnPlayer { awaitKill() }
            awaitOwnPlayer { awaitEnterVent() }
        }
        award()
    }
}