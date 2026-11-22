package com.fantamomo.mc.amongus.events.sabotage

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class LightLeverFlipEvent(override val player: AmongUsPlayer, val isOn: Boolean) : AmongUsEvent, PlayerAmongUsEvent