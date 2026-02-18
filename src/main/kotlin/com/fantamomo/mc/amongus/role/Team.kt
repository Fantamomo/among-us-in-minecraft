package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.role.imposters.ImposterRole
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
    data class NEUTRAL(val role: Role<*, *>) : Team(role.id, null, "neutral.${role.id}") {
        init {
            teams.add(this)
        }
    }

    companion object {
        private val teams: MutableSet<Team> = mutableSetOf(CREWMATES, IMPOSTERS)

        fun getTeams(): Set<Team> = teams
    }
}
