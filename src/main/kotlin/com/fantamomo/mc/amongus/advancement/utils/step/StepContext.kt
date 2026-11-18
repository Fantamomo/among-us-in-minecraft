package com.fantamomo.mc.amongus.advancement.utils.step

import com.fantamomo.mc.amongus.advancement.ExperimentalAdvancementApi

@ExperimentalAdvancementApi
data class StepContext(val step: StepInfo) {
    val id: String get() = step.key
    val languageKey: String get() = step.languageKey
}
