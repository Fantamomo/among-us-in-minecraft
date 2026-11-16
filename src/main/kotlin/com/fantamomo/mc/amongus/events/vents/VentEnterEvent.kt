package com.fantamomo.mc.amongus.events.vents

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class VentEnterEvent(override val player: AmongUsPlayer, /* TODO: specify vent*/) : AmongUsEvent, PlayerAmongUsEvent {
}