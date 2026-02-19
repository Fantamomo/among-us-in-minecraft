package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component

interface AssignedRole<R : Role<R, A>, A : AssignedRole<R, A>> {
    val definition: R
    val player: AmongUsPlayer

    val name: Component
        get() = definition.name
    val description: Component
        get() = definition.description

    fun onGameStart() {}
    fun onGameEnd() {}
    fun tick() {}

    fun scoreboardLine(): Component? = null

    fun hasWon(): Boolean = false
}