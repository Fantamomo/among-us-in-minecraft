package com.fantamomo.mc.amongus.events

import com.fantamomo.mc.amongus.player.AmongUsPlayer

class PlayerKillEvent(override val player: AmongUsPlayer, val victim: AmongUsPlayer) : AmongUsEvent, PlayerAmongUsEvent