package com.fantamomo.mc.amongus.settings

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.settings.types.*
import com.fantamomo.mc.amongus.util.data.DistanceEnum
import com.fantamomo.mc.amongus.util.data.TaskBarUpdateEnum
import net.kyori.adventure.text.Component
import org.bukkit.Material
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class SettingsKey<T : Any, S : SettingsType<T>>(
    val key: String,
    val type: S,
    val defaultValue: T,
    val settingsDisplayName: Component = Component.translatable(LANGUAGE_NAME_PREFIX + key),
    val settingsDescription: Component? = Component.translatable(LANGUAGE_DESCRIPTION_PREFIX + key),
    val group: SettingsGroup? = null
) {
    init {
        keys.add(this)
        group?.directKeys?.add(this)
    }

    companion object {
        private const val LANGUAGE_NAME_PREFIX = "settings.name."
        private const val LANGUAGE_DESCRIPTION_PREFIX = "settings.description."

        private val keys: MutableSet<SettingsKey<*, *>> = mutableSetOf()

        val groups: List<SettingsGroup> = listOf(VENT, MEETING, TASK, ROLES, KILL, MESSAGES, UTILS, MODIFIER, DEV)

        fun fromKey(key: String): SettingsKey<*, *>? = keys.find { it.key == key }
        fun keys(): Set<SettingsKey<*, *>> = keys
    }

    object VENT : SettingsGroup("vent", Material.TRIPWIRE_HOOK) {
        val VENT_DISTANCE = key("vent.distance", EnumSettingsType.create<DistanceEnum>(), DistanceEnum.NORMAL)
        val VENT_COOLDOWN = key("vent.cooldown", IntSettingsType.range(1, 60), 1)
        val VENT_VISIBLY_AS_WAYPOINT = key("vent.visibly.as.waypoint", BooleanSettingsType, false)
    }

    object MEETING : SettingsGroup("meeting", Material.COMPASS) {
        val MEETING_DISCUSSION_TIME =
            key("meeting.discussion.time", DurationSettingsType.range(Duration.ZERO, 3.minutes), 10.seconds)
        val MEETING_VOTING_TIME =
            key("meeting.voting.time", DurationSettingsType.range(15.seconds, 3.minutes), 60.seconds)
        val MEETING_BUTTONS = key("meeting.buttons", IntSettingsType.positive, 3)
        val MEETING_BUTTON_COOLDOWN =
            key("meeting.button.cooldown", DurationSettingsType.range(Duration.ZERO, 1.minutes), 15.seconds)
    }

    object TASK : SettingsGroup("task", Material.PAPER) {
        val TASK_COMMON = key("task.common", IntSettingsType.positive, 3)
        val TASK_SHORT = key("task.short", IntSettingsType.min(1), 6)
        val TASK_LONG = key("task.long", IntSettingsType.positive, 2)
        val TASK_BAR_UPDATE =
            key("task.bar.update", EnumSettingsType.create<TaskBarUpdateEnum>(), TaskBarUpdateEnum.IMMEDIATELY)
    }

    @Suppress("UnusedExpression", "ClassName")
    object ROLES : SettingsGroup("roles", Material.DIAMOND) {

        val IMPOSTERS = key("imposters", IntSettingsType.range(1, 3), 1)

        object ROLE_CHANCES : SettingsGroup("roles.chances", Material.COMPARATOR, parent = ROLES) {
            val roles = Role.roles.associateWith { role ->
                val roleName = Component.translatable("role.${role.id}.name")
                SettingsKey(
                    key = "roles.${role.id}",
                    type = PercentSettingsType,
                    defaultValue = 50,
                    settingsDisplayName = textComponent {
                        translatable("settings.name.role") { args { component("role", roleName) } }
                    },
                    settingsDescription = textComponent {
                        translatable("settings.description.role") { args { component("role", roleName) } }
                    },
                    group = ROLE_CHANCES
                )
            }
        }

        object MINER : SettingsGroup("roles.miner", Material.IRON_PICKAXE, parent = ROLES) {
            val CREATE_VENT_COOLDOWN =
                key("miner.create_vent.cooldown", DurationSettingsType.min(1.seconds), 45.seconds)
        }

        object MORPHLING : SettingsGroup("roles.morphling", Material.ARMOR_STAND, parent = ROLES) {
            val MORPH_COOLDOWN = key("morphling.morph.cooldown", DurationSettingsType.min(5.seconds), 30.seconds)
            val MORPH_DURATION = key("morphling.morph.duration", DurationSettingsType.min(5.seconds), 15.seconds)
        }

        object GHOST : SettingsGroup("roles.ghost", Material.PHANTOM_MEMBRANE, parent = ROLES) {
            val FORM_COOLDOWN = key("ghost.form.cooldown", DurationSettingsType.min(1.seconds), 30.seconds)
            val FORM_DURATION = key("ghost.form.duration", DurationSettingsType.min(1.seconds), 15.seconds)
        }

        object CANNIBAL : SettingsGroup("roles.cannibal", Material.ROTTEN_FLESH, parent = ROLES) {
            val BODIES_TO_EAT = key("roles.cannibal.bodies_to_eat", IntSettingsType.range(1, 10), 4)
        }

        object ARSONIST : SettingsGroup("roles.arsonist", Material.FLINT_AND_STEEL, parent = ROLES) {
            val DOUSE_COOLDOWN = key("arsonist.douse.cooldown", DurationSettingsType.min(1.seconds), 30.seconds)
            val DOUSE_DISTANCE =
                key("arsonist.douse.distance", EnumSettingsType.create<DistanceEnum>(), DistanceEnum.NORMAL)
        }

        object REVEAL_TEAM : SettingsGroup("roles.reveal_team", Material.SPYGLASS, parent = ROLES) {
            val START_COOLDOWN =
                key("reveal.team.start.cooldown", DurationSettingsType.min(1.seconds), 30.seconds)
            val COOLDOWN_INCREMENT =
                key("reveal.team.cooldown.increment", DurationSettingsType.min(0.seconds), 10.seconds)
            val DISTANCE =
                key("reveal.team.distance", EnumSettingsType.create<DistanceEnum>(), DistanceEnum.NORMAL)
        }

        object CAMOUFLAGE : SettingsGroup("roles.camouflage", Material.LEATHER, parent = ROLES) {
            val COOLDOWN = key("roles.camouflage.cooldown", DurationSettingsType.min(1.seconds), 30.seconds)
            val DURATION = key("roles.camouflage.duration", DurationSettingsType.min(1.seconds), 10.seconds)
        }

        init {
            // force init all sub objects
            ROLE_CHANCES; MINER; MORPHLING; GHOST; CANNIBAL; ARSONIST; REVEAL_TEAM; CAMOUFLAGE
        }
    }

    object KILL : SettingsGroup("kill", Material.BONE) {
        val KILL_DISTANCE = key("kill.distance", EnumSettingsType.create<DistanceEnum>(), DistanceEnum.NORMAL)
        val KILL_COOLDOWN = key("kill.cooldown", DurationSettingsType.min(1.seconds), 45.seconds)
    }

    object MESSAGES : SettingsGroup("messages", Material.BOOK) {
        val ALLOW_GHOST_MESSAGE_IN_GAME = key("allow.ghost.message.in.game", BooleanSettingsType, true)
        val ALLOW_IMPOSTER_PRIVATE_MESSAGE = key("allow.imposter.private.message", BooleanSettingsType, true)
    }

    object UTILS : SettingsGroup("utils", Material.COMPARATOR) {
        val CAMERA_SWITCH_SAFE_COOLDOWN = key("camera.switch.safe.cooldown", IntSettingsType.range(0, 1000), 750)
        val SABOTAGE_CRISIS_COOLDOWN = key("sabotage.crisis.cooldown", IntSettingsType.range(10, 300), 60)
    }

    object MODIFIER : SettingsGroup("modifier", Material.POTION) {
        val ENABLED = key("modifier.enabled", BooleanSettingsType, false)
    }

    object DEV : SettingsGroup("dev", Material.REDSTONE) {
        val DO_WIN_CHECK = key("dev.do.win.check", BooleanSettingsType, true)
        val DO_WIN_CHECK_ON_TICK = key("dev.do.win.check.on.tick", BooleanSettingsType, true)
    }
}