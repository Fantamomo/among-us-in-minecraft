package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Serializable

@Serializable
sealed interface  Statistic {
    val id: String

    companion object {
    }
}