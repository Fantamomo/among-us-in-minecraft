package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.manager.*
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.editStatistics
import com.fantamomo.mc.amongus.role.RoleManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.sabotage.SabotageManager
import com.fantamomo.mc.amongus.settings.Settings
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.TaskManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import kotlin.uuid.Uuid

class Game(
    area: GameArea,
    val world: World,
    val maxPlayers: Int = DEFAULT_MAX_PLAYERS
) {
    val area: GameArea

    init {
        require(area.isValid()) { "Area ${area.name} is not valid" }
        this.area = area.withWorld(world)
    }

    val code: String = createRandomCode()
    val uuid: Uuid = Uuid.random()

    val settings: Settings = Settings()

    val ventManager = VentManager(this)
    val cameraManager = CameraManager(this)
    val waypointManager = WaypointManager(this)
    val actionBarManager = ActionBarManager(this)
    val sabotageManager = SabotageManager(this)
    val roleManager = RoleManager(this)
    val taskManager = TaskManager(this)
    val meetingManager = MeetingManager(this)
    val killManager = KillManager(this)
    val scoreboardManager = ScoreboardManager(this)
    val chatManager = ChatManager(this)

    internal val players = mutableListOf<AmongUsPlayer>()
    var phase: GamePhase = GamePhase.LOBBY

    var resultMessage: Component? = null

    fun addPlayer(player: Player): Boolean {
        if (phase != GamePhase.LOBBY) return false
        if (players.size >= maxPlayers) return false
        if (PlayerManager.exists(player.uniqueId)) return false
        val newPlayer = PlayerManager.joinGame(player, this)
        scoreboardManager.addLobbyPlayer(newPlayer)
        return true
    }

    internal fun removePlayer0(player: AmongUsPlayer) {
        players.remove(player)
        ventManager.removePlayer0(player)
        cameraManager.leaveCams(player)
        waypointManager.removePlayer(player)
        actionBarManager.removeAll(player)
        sabotageManager.removePlayer(player)
        taskManager.removePlayer(player)
        scoreboardManager.removePlayer(player)
    }

    private var ticks = 0

    fun tick() {
        if (phase == GamePhase.LOBBY) {
            scoreboardManager.tick()
            return
        }
        if (phase == GamePhase.FINISHED) return
        ticks++
        if (ticks % 20 == 0 && settings[SettingsKey.DO_WIN_CHECK_ON_TICK]) {
            checkWin()
        }
        for (player in players) {
            player.player?.saturation = 5.0f
            player.player?.foodLevel = 20
        }
        ventManager.tick()
        cameraManager.tick()
        waypointManager.tick()
        actionBarManager.tick()
        sabotageManager.tick()
        taskManager.tick()
        meetingManager.tick()
        scoreboardManager.tick()
    }

    fun getPlayer(uuid: UUID) = players.find { it.uuid == uuid }

    internal fun randomDyeColor(): DyeColor {
        val entries = DyeColor.entries
        if (players.isEmpty()) return entries.random()
        val list = entries.toMutableList()
        for (player in players) list.remove(player.color)
        return list.random()
    }

    internal fun onDisconnected(player: AmongUsPlayer) {
        when (phase) {
            GamePhase.RUNNING,
            GamePhase.CALLING_MEETING,
            GamePhase.DISCUSSION,
            GamePhase.VOTING,
            GamePhase.ENDING_MEETING -> {
                sendChatMessage(textComponent {
                    content("${player.name} has disconnected from the game. He has 60 seconds to reconnect, before he will be killed.")
                })
            }
            else -> {}
        }

        meetingManager.meeting?.voteInventories?.remove(player)
        if (phase.onDisconnectRemove) removePlayer0(player)
    }

    internal fun onRejoin(amongUsPlayer: AmongUsPlayer) {
        amongUsPlayer.mannequinController.getEntity()?.location?.let { amongUsPlayer.player?.teleport(it) }
        val player = amongUsPlayer.player
        if (player != null) {
            for (ability in amongUsPlayer.abilities) {
                for (item in ability.items) {
                    player.inventory.addItem(item.get())
                }
            }
        }
        waypointManager.onPlayerRejoin(amongUsPlayer)
        scoreboardManager.onPlayerRejoin(amongUsPlayer)
    }

    fun sendChatMessage(component: Component) {
        players.forEach { it.player?.sendMessage(component) }
    }

    fun <T : Any> sendTitle(titlePart: TitlePart<T>, value: T) {
        players.forEach { it.player?.sendTitlePart(titlePart, value) }
    }

    fun invalidateAbilities() {
        AbilityManager.invalidateAll(this)
    }

    fun start() {
        phase = GamePhase.RUNNING
        roleManager.start()
        taskManager.start()
        val gameSpawn = area.gameSpawn ?: throw IllegalStateException("Game spawn not set")
        for (player in players) {
            player.editStatistics {
                statedGames.increment()
                playTime.timerStart()
            }
            player.player?.teleportAsync(gameSpawn)
            player.start()
        }
        scoreboardManager.start()
    }

    fun checkWin() {
        if (!settings[SettingsKey.DO_WIN_CHECK]) return
        if (taskManager.allTaskCompleted()) {
            letWin(Team.CREWMATES)
            return
        }
        val alivePlayers = players.filter { it.isAlive }
        val imposterCount = alivePlayers.count { it.assignedRole?.definition?.team == Team.IMPOSTERS }
        if (imposterCount == 0) {
            letWin(Team.CREWMATES)
            return
        }
        if (alivePlayers.size - imposterCount <= imposterCount) {
            letWin(Team.IMPOSTERS)
            return
        }
    }

    fun letWin(team: Team) {
        phase = GamePhase.FINISHED

        sabotageManager.endSabotage()
        invalidateAbilities()

        sendTitle(
            TitlePart.TITLE,
            textComponent {
                translatable(when (team) {
                    Team.CREWMATES -> "win.crewmate"
                    Team.IMPOSTERS -> "win.imposter"
                })
            }
        )

        killManager.removeAllCorpses()
        taskManager.end()

        waypointManager.end()
        actionBarManager.end()
        scoreboardManager.end()

        for (player in players) {
            if (cameraManager.isInCams(player)) cameraManager.leaveCams(player)
            if (ventManager.isVented(player)) ventManager.ventOut(player)

            val t = player.assignedRole?.definition?.team ?: Team.CREWMATES
            val hasWon = t == team

            player.editStatistics {
                val resultCount = when {
                    hasWon && team == Team.IMPOSTERS -> winsAsImposter
                    hasWon -> winsAsCrewmate
                    team == Team.IMPOSTERS -> losesAsImposter
                    else -> losesAsCrewmate
                }
                resultCount.increment()
                playTime.timerStop()
                playedGames.increment()
            }

            val subtitle = textComponent {
                if (hasWon) translatable("win.win") else translatable("win.lose")
            }
            val p = player.player
            if (p != null) {
                p.sendTitlePart(TitlePart.SUBTITLE, subtitle)
                for (online in Bukkit.getOnlinePlayers()) {
                    online.showPlayer(AmongUs, p)
                }
            }
            PlayerManager.gameEnds(player)
        }

        for (it in players.toList()) {
            removePlayer0(it)
        }

        EntityManager.dispose(this)
    }

    companion object {
        const val DEFAULT_MAX_PLAYERS = 16
        val CODE_CHARS = ('A'..'Z') + ('0'..'9')
        const val CODE_LENGTH = 4

        private fun createRandomCode(): String = (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")
    }
}