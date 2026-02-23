package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.adventure.text.append
import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import java.util.*

object PlayerManager {
    private val players = mutableListOf<AmongUsPlayer>()
    private var taskId = -1

    fun getPlayers(): List<AmongUsPlayer> = players

    fun exists(uuid: UUID) = players.any { it.uuid == uuid }

    fun getPlayer(uuid: UUID) = players.find { it.uuid == uuid }

    fun getPlayer(mannequin: Mannequin) = players.find { it.mannequinController.getEntity() == mannequin }

    fun getPlayer(name: String) = players.find { it.name == name }

    @NMS
    internal fun joinGame(player: Player, game: Game): AmongUsPlayer {
        if (exists(player.uniqueId)) throw IllegalStateException("Player already in a game")
        val auPlayer = AmongUsPlayer(player.uniqueId, player.name, game, player.location)
        auPlayer.player = player

        auPlayer.mannequinController.spawn()

        players.add(auPlayer)
        game.players.add(auPlayer)

        val nmsPlayer = (player as CraftPlayer).handle

        player.server.onlinePlayers.forEach {
            it.hidePlayer(AmongUs, player)
            @Suppress("UNNECESSARY_SAFE_CALL")
            (it as CraftPlayer).handle.connection?.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(nmsPlayer, true))
        }
        player.teleportAsync(game.area.lobbySpawn ?: throw IllegalStateException("Lobby spawn not set"))
            .thenAccept {
                auPlayer.wardrobeMannequin?.let { player.showEntity(AmongUs, it) }
            }
        player.inventory.clear()

        auPlayer.color = auPlayer.color
        return auPlayer
    }

    internal fun onPlayerQuit(player: Player) {
        val auPlayer = getPlayer(player.uniqueId) ?: return
        auPlayer.restorePlayer()

        if (auPlayer.game.phase.onDisconnectRemove) {
            auPlayer.modification?.onEnd()
            auPlayer.mannequinController.despawn()
            players.remove(auPlayer)
        }
        auPlayer.player = null
        auPlayer.game.onDisconnected(auPlayer)
    }

    internal fun gameEnds(amongUsPlayer: AmongUsPlayer) {
        amongUsPlayer.modification?.onGameEnd()
        amongUsPlayer.modification?.onEnd()
        amongUsPlayer.player?.inventory?.clear()
        amongUsPlayer.player = null
        amongUsPlayer.mannequinController.despawn()
        amongUsPlayer.statistics.onGameStop()
        players.remove(amongUsPlayer)
    }

    @NMS
    internal fun onPlayerJoin(player: Player) {
        val connection = (player as CraftPlayer).handle.connection
        for (playingPlayer in players) {
            val bukkitPlayer = playingPlayer.player ?: continue
            player.hidePlayer(AmongUs, bukkitPlayer)
            @Suppress("UNNECESSARY_SAFE_CALL")
            connection?.send(
                ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(
                    (bukkitPlayer as CraftPlayer).handle,
                    true
                )
            )
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
            @Suppress("UNNECESSARY_SAFE_CALL")
            (onlinePlayer as CraftPlayer).handle.connection?.send(
                ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(
                    player.handle, true
                )
            )
        }
        amongUsPlayer.mannequinController.hideFromSelf()
        amongUsPlayer.wardrobeMannequin?.let { player.showEntity(AmongUs, it) }
        amongUsPlayer.disconnectedAt = null
        game.onRejoin(amongUsPlayer)
    }

    fun getPlayer(player: Player): AmongUsPlayer? {
        val amongUsPlayer = getPlayer(player.uniqueId)
        amongUsPlayer?.player = player
        return amongUsPlayer
    }

    fun stop() {
        for (player in players) {
            player.restorePlayer()
        }
    }
}