package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.adventure.text.append
import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import org.bukkit.entity.Player
import java.util.*

object PlayerManager {
    private val players = mutableListOf<AmongUsPlayer>()

    fun getPlayers(): List<AmongUsPlayer> = players

    fun exists(uuid: UUID) = players.any { it.uuid == uuid }

    fun getPlayer(uuid: UUID) = players.find { it.uuid == uuid }

    internal fun joinGame(player: Player, game: Game) {
        if (exists(player.uniqueId)) throw IllegalStateException("Player already in a game")
        val amongUsPlayer = AmongUsPlayer(
            player.uniqueId,
            player.name,
            game,
            player.location
        )
        amongUsPlayer.player = player
        players.add(amongUsPlayer)
        game.players.add(amongUsPlayer)
    }

    internal fun onPlayerQuit(player: Player) {
        val amongUsPlayer = getPlayer(player.uniqueId) ?: return
        val game = amongUsPlayer.game
        game.onDisconnected(amongUsPlayer)
        amongUsPlayer.player = null
        if (game.phase.onDisconnectRemove) players.remove(amongUsPlayer)
    }

    internal fun onPlayerJoin(player: Player) {
        val amongUsPlayer = getPlayer(player.uniqueId) ?: return
        val game = amongUsPlayer.game
        if (game.phase == GamePhase.FINISHED) {
            player.sendMessage(textComponent {
                content("The game where you were playing has ended.")
                append(game.resultMessage ?: textComponent { content("No result") })
            })
            player.teleport(player.world.spawnLocation)
            players.remove(amongUsPlayer)
            return
        }
        amongUsPlayer.player = player
        val mannequin = amongUsPlayer.mannequin
        if (mannequin != null) {
            player.teleport(mannequin.location)
            mannequin.remove()
            amongUsPlayer.mannequin = null
        }
        game.onRejoin(amongUsPlayer)
    }

    fun getPlayer(player: Player): AmongUsPlayer? {
        val amongUsPlayer = getPlayer(player.uniqueId)
        amongUsPlayer?.player = player
        return amongUsPlayer
    }
}