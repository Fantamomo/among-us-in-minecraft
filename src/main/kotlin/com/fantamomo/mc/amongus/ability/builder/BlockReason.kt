package com.fantamomo.mc.amongus.ability.builder

import net.kyori.adventure.text.Component

sealed interface BlockReason {
    data object InMeeting : BlockReason {
        override val defaultBlockMessage = Component.translatable("ability.general.disabled.in_meeting")
    }

    data object Sabotage : BlockReason {
        override val defaultBlockMessage = Component.translatable("ability.general.disabled.sabotage")
    }

    data object InVent : BlockReason {
        override val defaultBlockMessage = Component.translatable("ability.general.disabled.in_vent")
    }

    data object Dead : BlockReason
    data object LimitReached : BlockReason
    data object GhostForm : BlockReason {
        override val defaultBlockMessage = Component.translatable("ability.general.disabled.ghost_form")
    }

    data class Custom(val id: String) : BlockReason

    val defaultBlockMessage: Component?
        get() = null

    companion object {
        operator fun invoke(id: String) = Custom(id)
        fun custom(id: String) = Custom(id)
    }
}