package com.fantamomo.mc.amongus.role

enum class Team {
    CREWMATES,
    IMPOSTERS;

    val canDoTask: Boolean
        get() = this == CREWMATES
}
