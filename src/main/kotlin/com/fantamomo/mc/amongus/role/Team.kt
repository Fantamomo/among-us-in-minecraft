package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.role.imposters.ImposterRole

enum class Team {
    CREWMATES,
    IMPOSTERS;

    val canDoTask: Boolean
        get() = this == CREWMATES

    val defaultRole: Role<*, *>
        get() = when (this) {
            CREWMATES -> CrewmateRole
            IMPOSTERS -> ImposterRole
        }
}
