package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.role.imposters.ImposterRole
import com.fantamomo.mc.amongus.role.neutral.ArsonistRole
import com.fantamomo.mc.amongus.role.neutral.CannibalRole
import com.fantamomo.mc.amongus.role.neutral.JesterRole
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

sealed class Team(val name: String, private val default: Role<*, *>?, val textColor: TextColor, val id: String = name) {

    init {
        require(id.isNotBlank()) { "Team ID cannot be blank" }
        require(default != null || id != name) { "Default role must be provided for non-neutral teams" }
    }

    open val canDoTask: Boolean
        get() = this === CREWMATES

    /** If the Sheriff can kill someone from this team without dying */
    open val canByKilledBySheriff: Boolean
        get() = this === IMPOSTERS || this is NEUTRAL

    val defaultRole: Role<*, *>
        get() = default ?: throw IllegalStateException("$id team has no default role")

    val description = Component.translatable("team.$id")

    data object CREWMATES : Team("crewmates", CrewmateRole, NamedTextColor.BLUE)
    data object IMPOSTERS : Team("imposters", ImposterRole, NamedTextColor.RED)

    @ConsistentCopyVisibility
    data class NEUTRAL private constructor(
        val role: Role<*, *>
    ) : Team(
        role.id, null, NamedTextColor.LIGHT_PURPLE, "neutral.${role.id}"
    ) {
        companion object {
            val JESTER by lazy { NEUTRAL(JesterRole) }
            val CANNIBAL by lazy { NEUTRAL(CannibalRole) }
            val ARSONIST by lazy { NEUTRAL(ArsonistRole) }
        }
    }

    companion object {
        // using lazy initialization for teams, because CREWMATES is due to unknown reason `null`, but IMPOSTERS and JESTER are not
        val teams: Set<Team> by lazy {
            setOf(
                CREWMATES,
                IMPOSTERS,
                NEUTRAL.JESTER,
                NEUTRAL.CANNIBAL,
                NEUTRAL.ARSONIST
            )
        }
    }
}
