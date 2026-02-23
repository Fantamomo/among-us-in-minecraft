package com.fantamomo.mc.amongus.modification

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component

interface AssignedModification<M : Modification<M, A>, A : AssignedModification<M, A>> {
    val definition: M
    val player: AmongUsPlayer

    val name: Component
        get() = definition.name
    val description: Component
        get() = definition.description

    fun scoreboardLine(): Component? = null

    fun onStart() {}
    fun onGameStart() {}

    fun onTick() {}

    fun onGameEnd() {}
    fun onEnd() {}
}