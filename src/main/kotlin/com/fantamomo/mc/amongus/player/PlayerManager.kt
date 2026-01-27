package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.adventure.text.append
import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import java.util.*

object PlayerManager {
    private val players = mutableListOf<AmongUsPlayer>()
    private var taskId = -1

    init {
        taskId = AmongUs.server.scheduler.scheduleSyncRepeatingTask(AmongUs, ::tick, 0L, 1L)
    }

    fun getPlayers(): List<AmongUsPlayer> = players

    fun exists(uuid: UUID) = players.any { it.uuid == uuid }

    fun getPlayer(uuid: UUID) = players.find { it.uuid == uuid }

    fun getPlayer(mannequin: Mannequin) = players.find { it.mannequinController.getEntity() == mannequin }

    internal fun joinGame(player: Player, game: Game) {
        if (exists(player.uniqueId)) throw IllegalStateException("Player already in a game")
        val auPlayer = AmongUsPlayer(player.uniqueId, player.name, game, player.location)
        auPlayer.player = player

        auPlayer.mannequinController.spawn()

        players.add(auPlayer)
        game.players.add(auPlayer)

        player.server.onlinePlayers.forEach {
            it.hidePlayer(AmongUs, player)
        }
        player.teleportAsync(game.area.lobbySpawn ?: throw IllegalStateException("Lobby spawn not set"))
        player.inventory.clear()
    }

    internal fun onPlayerQuit(player: Player) {
        val auPlayer = getPlayer(player.uniqueId) ?: return
        auPlayer.player = null

        if (auPlayer.game.phase.onDisconnectRemove) {
            auPlayer.mannequinController.despawn()
            players.remove(auPlayer)
        }
    }

    internal fun gameEnds(amongUsPlayer: AmongUsPlayer) {
        amongUsPlayer.player?.inventory?.clear()
        amongUsPlayer.player = null
        amongUsPlayer.mannequinController.despawn()
        players.remove(amongUsPlayer)
    }

    internal fun onPlayerJoin(player: Player) {
        for (playingPlayer in players) {
            player.hidePlayer(AmongUs, playingPlayer.player ?: continue)
        }
        val amongUsPlayer = getPlayer(player.uniqueId) ?: return
        val game = amongUsPlayer.game
        if (game.phase == GamePhase.FINISHED) {
            player.sendMessage(textComponent {
                content("The game where you were playing has ended.")
                append(game.resultMessage ?: textComponent { content("No result") })
            })
            player.teleportAsync(player.world.spawnLocation)
            amongUsPlayer.mannequinController.despawn()
            players.remove(amongUsPlayer)
            return
        }
        amongUsPlayer.player = player
        for (onlinePlayer in player.server.onlinePlayers) {
            onlinePlayer.hidePlayer(AmongUs, player)
        }
        amongUsPlayer.mannequinController.hideFromSelf()
        game.onRejoin(amongUsPlayer)
    }

    fun getPlayer(player: Player): AmongUsPlayer? {
        val amongUsPlayer = getPlayer(player.uniqueId)
        amongUsPlayer?.player = player
        return amongUsPlayer
    }

    private fun tick() {
        players.forEach {
            it.mannequinController.syncFromPlayer()
        }
    }
}