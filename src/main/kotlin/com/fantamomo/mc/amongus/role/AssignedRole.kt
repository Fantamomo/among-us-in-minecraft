package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface AssignedRole<R : Role<R, A>, A : AssignedRole<R, A>> {
    val definition: R
    val player: AmongUsPlayer

    fun onGameStart() {}
    fun onGameEnd() {}
    fun tick() {}
}