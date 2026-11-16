package com.fantamomo.mc.amongus.events

import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface PlayerAmongUsEvent : AmongUsEvent {
    val player: AmongUsPlayer
}