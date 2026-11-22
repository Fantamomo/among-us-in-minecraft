package com.fantamomo.mc.amongus.events.roles

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.manager.MorphManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer

class MorphStartEvent(val morphedPlayer: MorphManager.MorphedPlayer) : AmongUsEvent, PlayerAmongUsEvent {
    override val player: AmongUsPlayer get() = morphedPlayer.player
}