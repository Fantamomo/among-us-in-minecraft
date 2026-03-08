package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.util.WinCheckPhase
import com.fantamomo.mc.amongus.util.TickContext
import net.kyori.adventure.text.Component

interface AssignedRole<R : Role<R, A>, A : AssignedRole<R, A>> {
    val definition: R
    val player: AmongUsPlayer

    val name: Component
        get() = definition.name
    val description: Component
        get() = definition.description

    val winCheckPhase: WinCheckPhase
        get() = WinCheckPhase.POST

    fun onGameStart() {}
    fun onGameEnd() {}
    fun tick(tickContext: TickContext) {}

    fun scoreboardLine(): Component? = null

    fun hasWon(): Boolean = false

    fun gameEndInfo(): Component? = null
}