package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.SequencedAdvancement
import com.fantamomo.mc.amongus.advancement.awaitOwnPlayer
import com.fantamomo.mc.amongus.advancement.runner
import kotlin.time.Duration.Companion.seconds

data object FastVentKillAdvancement : SequencedAdvancement("fast_vent_kill") {
    override val runner = runner {
        awaitOwnPlayer { awaitExitVent() }
        withTimeout(10.seconds) {
            awaitOwnPlayer { awaitKill() }
            awaitOwnPlayer { awaitEnterVent() }
        }
    }
}