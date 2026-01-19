package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.command.arguments.DurationArgumentType
import com.fantamomo.mc.amongus.settings.SettingsType
import kotlin.time.Duration

class DurationSettingsType(min: Duration?, max: Duration?) : SettingsType<Duration> {
    override val type = Duration::class
    override val argumentType = DurationArgumentType(min, max)

    companion object {
        val positive = DurationSettingsType(Duration.ZERO, null)
        val negative = DurationSettingsType(null, Duration.ZERO)
        fun range(min: Duration, max: Duration) = DurationSettingsType(min, max)
        fun min(min: Duration) = DurationSettingsType(min, null)
        fun max(max: Duration) = DurationSettingsType(null, max)
    }
}