package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.role.imposters.ImposterRole
import com.fantamomo.mc.amongus.role.neutral.JesterRole
import net.kyori.adventure.text.Component

sealed class Team(val name: String, private val default: Role<*, *>?, val id: String = name) {

    init {
        require(id.isNotBlank()) { "Team ID cannot be blank" }
        require(default != null || id != name) { "Default role must be provided for non-neutral teams" }

    }

    open val canDoTask: Boolean
        get() = this === CREWMATES

    val defaultRole: Role<*, *>
        get() = default ?: throw IllegalStateException("$id team has no default role")

    val description = Component.translatable("team.$id")

    data object CREWMATES : Team("crewmates", CrewmateRole)
    data object IMPOSTERS : Team("imposters", ImposterRole)
    @ConsistentCopyVisibility
    data class NEUTRAL private constructor(val role: Role<*, *>) : Team(role.id, null, "neutral.${role.id}") {
        companion object {
            val JESTER = NEUTRAL(JesterRole)
        }
    }

    companion object {
        // using lazy initialization for teams, because CREWMATES is due to unknown reason `null`, but IMPOSTERS and JESTER are not
        val teams: Set<Team> by lazy { setOf(CREWMATES, IMPOSTERS, NEUTRAL.JESTER) }
    }
}
