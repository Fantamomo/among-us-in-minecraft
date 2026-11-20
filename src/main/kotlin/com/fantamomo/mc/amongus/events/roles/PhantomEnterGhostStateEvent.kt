package com.fantamomo.mc.amongus.events.roles

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class PhantomEnterGhostStateEvent(override val player: AmongUsPlayer) : AmongUsEvent, PlayerAmongUsEvent