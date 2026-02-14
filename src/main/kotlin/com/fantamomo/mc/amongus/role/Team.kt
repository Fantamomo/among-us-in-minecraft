package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.role.imposters.ImposterRole
import net.kyori.adventure.text.Component

enum class Team {
    CREWMATES,
    IMPOSTERS;

    val canDoTask: Boolean
        get() = this == CREWMATES

    val description = Component.translatable("team.${name.lowercase()}")

    val defaultRole: Role<*, *>
        get() = when (this) {
            CREWMATES -> CrewmateRole
            IMPOSTERS -> ImposterRole
        }
}
