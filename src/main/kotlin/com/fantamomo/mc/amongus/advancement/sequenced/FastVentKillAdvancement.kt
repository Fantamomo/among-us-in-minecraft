package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.ExperimentalAdvancementApi
import com.fantamomo.mc.amongus.advancement.SequencedAdvancement
import com.fantamomo.mc.amongus.advancement.awaitOwnPlayer
import com.fantamomo.mc.amongus.advancement.createRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAdvancementApi::class)
data object FastVentKillAdvancement : SequencedAdvancement("fast_vent_kill") {
    override val runner = createRunner {
        awaitOwnPlayer { awaitExitVent() }
        withTimeout(10.seconds) {
            awaitOwnPlayer { awaitKill() }
            awaitOwnPlayer { awaitEnterVent() }
        }
    }
}