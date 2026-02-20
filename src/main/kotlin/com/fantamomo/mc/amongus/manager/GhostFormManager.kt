package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.ability.builder.AbilityTimer
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class GhostFormManager(val game: Game) {
    private val players: MutableMap<AmongUsPlayer, GhostPlayer> = mutableMapOf()

    class GhostPlayer(
        val player: AmongUsPlayer,
        val start: Instant,
        val ghostCooldown: AbilityTimer?
    ) {
        val remainingTime: Duration
            get() = (player.game.settings[SettingsKey.ROLES.GHOST.FORM_DURATION] - (Clock.System.now() - start)).takeIf { it > Duration.ZERO } ?: Duration.ZERO

        init {
            player.mannequinController.freezeWithPhysics()
            player.mannequinController.showToSelf()
        }

        internal fun exit() {
            ghostCooldown?.start(player.game.settings[SettingsKey.ROLES.GHOST.FORM_COOLDOWN])
            player.mannequinController.also { controller ->
                controller.getEntity()?.location?.let { player.player?.teleport(it) }
                controller.unfreeze()
                controller.hideFromSelf()
            }
        }
    }

    fun isInGhostForm(player: AmongUsPlayer) = player in players

    fun getGhostPlayer(player: AmongUsPlayer) = players[player]

    fun tick() {
        players.values.removeIf { ghostPlayer ->
            val remainingTime = ghostPlayer.remainingTime
            (remainingTime <= Duration.ZERO).also { if (it) ghostPlayer.exit() }
        }
    }

    fun exitAll() {
        players.values.forEach { it.exit() }
        players.clear()
    }

    fun joinGhostForm(player: AmongUsPlayer, ghostCooldown: AbilityTimer? = null) {
        if (isInGhostForm(player)) return
        players[player] = GhostPlayer(player, Clock.System.now(), ghostCooldown)
    }

    fun exit(player: AmongUsPlayer) {
        players.remove(player)?.exit()
    }
}