package com.fantamomo.mc.amongus.ability.builder

sealed interface BlockReason {
    data object InMeeting : BlockReason
    data object Sabotage : BlockReason
    data object InVent : BlockReason
    data object Dead : BlockReason
    data object LimitReached : BlockReason
    data class Custom(val id: String) : BlockReason

    companion object {
        operator fun invoke(id: String) = Custom(id)
    }
}