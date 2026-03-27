package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.adventure.text.append
import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.bot.BotName
import com.fantamomo.mc.amongus.util.internal.NMS
import com.mojang.authlib.GameProfile
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.RemoteChatSession
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.level.GameType
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import java.util.*
import kotlin.uuid.toKotlinUuid

object PlayerManager {
    private val players = mutableListOf<AmongUsPlayer>()

    private val playerAddPacketActions = EnumSet.of(
        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER
    )

    private val clientboundPlayerInfoUpdatePacketEntryNewInstance: (
        uuid: UUID,
        profile: GameProfile,
        listed: Boolean,
        latency: Int,
        gameMode: GameType,
        displayName: Component,
        showHat: Boolean,
        playerListOrder: Int
    ) -> ClientboundPlayerInfoUpdatePacket.Entry = run {
        val clazz = ClientboundPlayerInfoUpdatePacket.Entry::class.java
        val method = clazz.getDeclaredConstructor(
            UUID::class.java,
            GameProfile::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            GameType::class.java,
            Component::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            RemoteChatSession.Data::class.java,
        )
        method.isAccessible = true
        return@run {
                uuid: UUID,
                profile: GameProfile,
                listed: Boolean,
                latency: Int,
                gameMode: GameType,
                displayName: Component,
                showHat: Boolean,
                playerListOrder: Int,
            ->
            method.newInstance(
                uuid,
                profile,
                listed,
                latency,
                gameMode,
                displayName,
                showHat,
                playerListOrder,
                null
            )
        }
    }

    fun getPlayers(): List<AmongUsPlayer> = players

    fun exists(uuid: UUID) = players.any { it.uuid == uuid }

    fun getPlayer(uuid: UUID) = players.find { it.uuid == uuid }

    fun getHumanPlayer(uuid: UUID) = players.find { it.uuid == uuid } as? HumanAmongUsPlayer

    fun getPlayer(mannequin: Mannequin) = players.find { it.mannequinController.getEntity() == mannequin }

    fun getPlayer(name: String) = players.find { it.name.equals(name, ignoreCase = true) }

    @NMS
    internal fun joinGame(player: Player, game: Game): HumanAmongUsPlayer {
        if (exists(player.uniqueId)) throw IllegalStateException("Player already in a game")
        val auPlayer = HumanAmongUsPlayer(player.uniqueId, player.name, game, player.location)
        LastPlayerLocationManager.set(player.uniqueId, player.location)
        auPlayer.player = player

        auPlayer.mannequinController.spawn()

        AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
            auPlayer.mannequinController.showToAll()
        }, 1L)

        players.add(auPlayer)
        game.players.add(auPlayer)

        val nmsPlayer = (player as CraftPlayer).handle

        val packet = ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(
            nmsPlayer,
            true
        )
        player.server.onlinePlayers.forEach {
            it.hidePlayer(AmongUs, player)
            @Suppress("UNNECESSARY_SAFE_CALL")
            (it as CraftPlayer).handle.connection?.send(packet)
        }
        player.teleportAsync(game.area.lobbySpawn ?: throw IllegalStateException("Lobby spawn not set"))
            .thenAccept {
                auPlayer.wardrobeMannequin?.let { player.showEntity(AmongUs, it) }
                AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
                    auPlayer.mannequinController.showToSelf()
                    auPlayer.mannequinController.hideFromSelf()
                    game.scoreboardManager.addLobbyPlayer(auPlayer)
                }, 2L)
            }
        player.inventory.clear()

        auPlayer.updateHelmet()
        return auPlayer
    }

    @NMS
    internal fun addBot(name: BotName, game: Game): BotAmongUsPlayer {
        val auPlayer = BotAmongUsPlayer(UUID.randomUUID(), game, name)

        auPlayer.mannequinController.spawn()

        val entry = clientboundPlayerInfoUpdatePacketEntryNewInstance.invoke(
            auPlayer.uuid,
            name.gameProfile,
            true,
            0,
            GameType.ADVENTURE,
            Component.literal(name.name),
            true,
            0,
        )

        val packet = ClientboundPlayerInfoUpdatePacket(
            playerAddPacketActions,
            entry,
        )

        for (player in game.players) {
            if (player.isBot) continue
            val connection = (player.player as? CraftPlayer)?.handle?.connection ?: continue
            connection.send(packet)
        }

        players.add(auPlayer)
        game.players.add(auPlayer)
        auPlayer.updateHelmet()
        return auPlayer
    }

    internal fun onPlayerQuit(player: Player) {
        val auPlayer = getHumanPlayer(player.uniqueId) ?: return
        auPlayer.restorePlayer()

        if (auPlayer.game.phase.onDisconnectRemove) {
            auPlayer.modification?.onEnd()
            auPlayer.mannequinController.despawn()
            players.remove(auPlayer)
        }
        auPlayer.player = null
        auPlayer.game.onDisconnected(auPlayer)
    }

    internal fun gameEnds(amongUsPlayer: AmongUsPlayer, teleport: Boolean = true) {
        amongUsPlayer.modification?.onGameEnd()
        amongUsPlayer.modification?.onEnd()
        if (amongUsPlayer.isHuman) {
            val player = amongUsPlayer.player
            if (player != null) {
                if (teleport) player.teleportAsync(amongUsPlayer.locationBeforeGame)
                player.inventory.clear()
                amongUsPlayer.player = null
            }
        }
        amongUsPlayer.mannequinController.despawn()
        amongUsPlayer.humanOrNull?.statistics?.onGameStop()
        players.remove(amongUsPlayer)
    }

    fun leaveGame(player: AmongUsPlayer, teleport: Boolean = true) {
        val game = player.game
        if (game.phase != GamePhase.LOBBY) return
        if (player.isHuman) {
            val p = player.player
            val clearPlayer = game.leavePlayer(player, teleport)
            if (p != null) {
                p.inventory.clear()
                for (online in Bukkit.getOnlinePlayers()) {
                    online.showPlayer(AmongUs, p)
                }
            }
            if (clearPlayer) player.player = null
            player.wardrobeMannequin?.remove()
            player.mannequinController.despawn()
        }
        players.remove(player)
    }

    @NMS
    internal fun onPlayerJoin(player: Player) {
        @Suppress("RedundantNullableReturnType")
        val connection: ServerGamePacketListenerImpl? = (player as CraftPlayer).handle.connection
        for (playingPlayer in players) {
            playingPlayer.mannequinController.updateNameTag(player, force = true)
            val bukkitPlayer = playingPlayer.humanOrNull?.player ?: continue
            player.hidePlayer(AmongUs, bukkitPlayer)
            connection?.send(
                ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(
                    (bukkitPlayer as CraftPlayer).handle,
                    true
                )
            )
        }
        val amongUsPlayer = getPlayer(player)

        if (amongUsPlayer == null) {
            if (GameManager.isGameWorld(player.world)) {
                player.teleportAsync(AmongUs.server.respawnWorld.spawnLocation)
            }
            return
        }

        val game = amongUsPlayer.game
        if (game.phase == GamePhase.FINISHED) {
            player.sendMessage(textComponent {
                content("The game where you were playing has ended.")
                append(game.resultMessage ?: textComponent { content("No result") })
            })
            player.teleportAsync(player.world.spawnLocation)
            LastPlayerLocationManager.remove(amongUsPlayer.uuid.toKotlinUuid())
            amongUsPlayer.mannequinController.despawn()
            players.remove(amongUsPlayer)
            return
        }
        for (onlinePlayer in player.server.onlinePlayers) {
            amongUsPlayer.mannequinController.updateNameTag(onlinePlayer, force = true)
            onlinePlayer.hidePlayer(AmongUs, player)
            @Suppress("UNNECESSARY_SAFE_CALL")
            (onlinePlayer as CraftPlayer).handle.connection?.send(
                ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(
                    player.handle, true
                )
            )
        }
        if (connection != null) {
            for (playingPlayer in game.players) {
                if (playingPlayer.isHuman) continue

                val entry = clientboundPlayerInfoUpdatePacketEntryNewInstance.invoke(
                    playingPlayer.uuid,
                    playingPlayer.botName.gameProfile,
                    true,
                    0,
                    GameType.ADVENTURE,
                    Component.literal(playingPlayer.name),
                    true,
                    0,
                )

                val packet = ClientboundPlayerInfoUpdatePacket(
                    playerAddPacketActions,
                    entry,
                )
                connection.send(packet)
            }
        }
        amongUsPlayer.mannequinController.hideFromSelf()
        amongUsPlayer.wardrobeMannequin?.let { player.showEntity(AmongUs, it) }
        amongUsPlayer.disconnectedAt = null
        amongUsPlayer.lastSeen = game.phase
        game.onRejoin(amongUsPlayer)
    }

    fun getPlayer(player: Player): HumanAmongUsPlayer? {
        val amongUsPlayer = getHumanPlayer(player.uniqueId)
        amongUsPlayer?.player = player
        return amongUsPlayer
    }

    fun stop() {
        for (player in players) {
            if (player.isBot) continue
            player.player?.teleportAsync(player.locationBeforeGame)
            player.player?.inventory?.clear()
            player.restorePlayer()
        }
        players.clear()
    }
}