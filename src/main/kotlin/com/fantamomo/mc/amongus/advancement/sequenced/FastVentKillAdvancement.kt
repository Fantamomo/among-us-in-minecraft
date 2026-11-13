package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.SequencedAdvancement
import com.fantamomo.mc.amongus.advancement.awaitOwnPlayer
import com.fantamomo.mc.amongus.advancement.runner
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

data object FastVentKillAdvancement : SequencedAdvancement("fast_vent_kill") {
    private val logger = LoggerFactory.getLogger(FastVentKillAdvancement::class.java)

    override val runner = runner {
        awaitOwnPlayer { awaitExitVent() }
        withTimeout(10.seconds) {
            awaitOwnPlayer { awaitKill() }
            awaitOwnPlayer { awaitEnterVent() }
        }
    }
}