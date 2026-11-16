package com.fantamomo.mc.amongus.events.player

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class PlayerKillEvent(override val player: AmongUsPlayer, val victim: AmongUsPlayer) : AmongUsEvent, PlayerAmongUsEvent