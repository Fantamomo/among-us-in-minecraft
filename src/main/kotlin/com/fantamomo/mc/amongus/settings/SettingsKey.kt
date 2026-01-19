package com.fantamomo.mc.amongus.settings

import com.fantamomo.mc.amongus.settings.types.BooleanSettingsType
import com.fantamomo.mc.amongus.settings.types.DurationSettingsType
import com.fantamomo.mc.amongus.settings.types.EnumSettingsType
import com.fantamomo.mc.amongus.settings.types.IntSettingsType
import com.fantamomo.mc.amongus.util.data.DistanceEnum
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class SettingsKey<T : Any, S : SettingsType<T>>(
    val key: String,
    val type: S,
    val defaultValue: T,
    val settingsDisplayName: String = LANGUAGE_NAME_PREFIX + key,
    val settingsDescription: String? = null
) {
    init {
        keys.add(this)
    }

    companion object {
        private const val LANGUAGE_NAME_PREFIX = "settings.name."
        private const val LANGUAGE_DESCRIPTION_PREFIX = "settings.description."
        private val keys: MutableSet<SettingsKey<*, *>> = mutableSetOf()

        fun keys(): Set<SettingsKey<*, *>> = keys

        fun <T : Any, S : SettingsType<T>> key(
            key: String,
            type: S,
            defaultValue: T,
            hasDescription: Boolean = false
        ) = SettingsKey(
            key,
            type,
            defaultValue,
            settingsDescription = if (hasDescription) LANGUAGE_DESCRIPTION_PREFIX + key else null
        )

        val VENT_DISTANCE = key("vent.distance", EnumSettingsType.create<DistanceEnum>(), DistanceEnum.NORMAL)
        val VENT_COOLDOWN = key("vent.cooldown", IntSettingsType.range(1, 60), 1)
        val VENT_VISIBLY_AS_WAYPOINT = key("vent.visibly.as.waypoint", BooleanSettingsType, false)
        val CAMERA_SWITCH_SAFE_COOLDOWN = key("camera.switch.safe.cooldown", IntSettingsType.range(0, 1000), 750)

        val SABOTAGE_CRISIS_COOLDOWN = key("sabotage.crisis.cooldown", IntSettingsType.range(10, 300), 60)
        val MEETING_DISCUSSION_TIME =
            key("meeting.discussion.time", DurationSettingsType.range(Duration.ZERO, 3.minutes), 1.minutes)
        val MEETING_VOTING_TIME = key("meeting.voting.time", DurationSettingsType.range(15.seconds, 3.minutes), 30.seconds)
        val MEETING_BUTTONS = key("meeting.buttons", IntSettingsType.positive, 3)
        val MEETING_BUTTON_COOLDOWN = key("meeting.button.cooldown", DurationSettingsType.range(Duration.ZERO, 1.minutes), 15.seconds)
    }
}