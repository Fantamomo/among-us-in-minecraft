package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.*
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.role.RoleManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.sabotage.SabotageManager
import com.fantamomo.mc.amongus.settings.Settings
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.toSmartString
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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

    val settings: Settings = Settings(this)

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
    val morphManager = MorphManager(this)
    val ghostFormManager = GhostFormManager(this)

    internal val players = mutableListOf<AmongUsPlayer>()
    var phase: GamePhase = GamePhase.LOBBY

    var resultMessage: Component? = null

    fun addPlayer(player: Player): Boolean {
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return false
        if (players.size >= maxPlayers) return false
        if (PlayerManager.exists(player.uniqueId)) return false
        val newPlayer = PlayerManager.joinGame(player, this)
        scoreboardManager.addLobbyPlayer(newPlayer)
        abortStartCooldown()
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
        morphManager.removePlayer(player)
    }

    internal var ticks = 0
        private set

    fun tick() {
        ticks++
        if (phase == GamePhase.LOBBY || phase == GamePhase.STARTING) {
            for (player in players) {
                player.mannequinController.syncFromPlayer()
            }
            scoreboardManager.tick()

            if (startCooldownTicks > ticks) {
                val remainingTicks = startCooldownTicks - ticks
                val remaining = (remainingTicks + 19) / 20

                val tickInSecond = remainingTicks % 20
                val chars = ('0'..'9') + ('A'..'Z')

                val target = remaining.toString()
                val length = target.length

                val display = buildString {
                    for (i in 0 until length) {

                        val stopThreshold = 6 + (i * 4)

                        if (tickInSecond > stopThreshold) {
                            append(chars.random())
                        } else {
                            append(target[i])
                        }
                    }
                }

                val color = when {
                    remaining <= 3 -> NamedTextColor.DARK_RED
                    remaining <= 5 -> NamedTextColor.RED
                    else -> NamedTextColor.GOLD
                }

                val title = Title.title(
                    Component.text(display)
                        .color(color)
                        .decorate(TextDecoration.BOLD),
                    Component.empty(),
                    0,
                    20,
                    0
                )

                for (player in players) {
                    val p = player.player ?: continue
                    p.showTitle(title)
                }
            } else if (startCooldownTicks == ticks) {
                startCooldownTicks = -1
                start()
            }

            return
        }
        if (phase == GamePhase.FINISHED) return
        if (ticks % 20 == 0 && settings[SettingsKey.DEV.DO_WIN_CHECK_ON_TICK]) {
            checkWin()
        }
        ventManager.tick()
        cameraManager.tick()
        waypointManager.tick()
        actionBarManager.tick()
        sabotageManager.tick()
        taskManager.tick()
        meetingManager.tick()
        scoreboardManager.tick()
        roleManager.tick()
        morphManager.tick()
        ghostFormManager.tick()

        val now = Clock.System.now()

        for (player in players) {
            player.player?.saturation = 5.0f
            player.player?.foodLevel = 20
            player.modification?.onTick()
            player.mannequinController.syncFromPlayer()
            val disconnectedAt = player.disconnectedAt ?: continue
            if (now - disconnectedAt < MAX_DISCONNECT_TIME) continue
            killPlayer(player)
        }
    }

    private fun killPlayer(player: AmongUsPlayer) {
        killManager.kill(player, false)
        taskManager.removePlayer(player)
        player.abilities.clear()
        player.disconnectedAt = null
        sendChatMessage(textComponent {
            translatable("game.disconnected.killed") {
                args {
                    string("player", player.name)
                }
            }
        })
    }

    fun getPlayer(uuid: UUID) = players.find { it.uuid == uuid }

    internal fun randomPlayerColor(): PlayerColor {
        if (players.isEmpty()) return PlayerColor.random()
        val list = PlayerColor.notRestrictedColors()
        for (player in players) list.remove(player.color)
        return list.random()
    }

    internal fun onDisconnected(player: AmongUsPlayer) {
        player.disconnectedAt = Clock.System.now()
        when (phase) {
            GamePhase.RUNNING,
            GamePhase.CALLING_MEETING,
            GamePhase.DISCUSSION,
            GamePhase.VOTING,
            GamePhase.ENDING_MEETING -> {
                sendChatMessage(textComponent {
                    translatable("game.disconnected") {
                        args {
                            string("player", player.name)
                            string("time", MAX_DISCONNECT_TIME.toSmartString())
                        }
                    }
                })
            }

            else -> {}
        }

        meetingManager.meeting?.voteInventories?.remove(player)
        if (phase.onDisconnectRemove) removePlayer0(player)
    }

    internal fun isColorFree(color: PlayerColor) = players.none { it.color == color }

    internal fun updateAllWardrobeInventories() {
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return
        val cooldowns = PlayerColor.entries.associateWith { if (isColorFree(it)) 0 else Int.MAX_VALUE / 2 }
        for (player in players) {
            val p = player.player ?: continue
            val topInventory = p.openInventory.topInventory
            val holder = topInventory.holder as? WardrobeInventory ?: continue
            holder.update()
            for (entry in cooldowns) {
                p.setCooldown(entry.key.cooldownGroup, entry.value)
            }
        }
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
            for (other in players) {
                other.mannequinController.updateNameTag(player)
            }
        }
        waypointManager.onPlayerRejoin(amongUsPlayer)
        scoreboardManager.onPlayerRejoin(amongUsPlayer)
        killManager.onPlayerRejoin(amongUsPlayer)
        amongUsPlayer.modification?.onStart()
        if (!amongUsPlayer.isAlive) amongUsPlayer.addGhostImprovements()
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

    internal var startCooldownTicks = -1
        private set

    fun startStartCooldown() {
        if (phase != GamePhase.LOBBY) return
        phase = GamePhase.STARTING
        startCooldownTicks = ticks + 200
    }

    fun abortStartCooldown() {
        if (phase != GamePhase.STARTING) return
        phase = GamePhase.LOBBY
        startCooldownTicks = -1
        sendTitle(TitlePart.TIMES, Title.DEFAULT_TIMES)
        val abortStartMessage = Component.translatable("game.start.aborted")
        sendTitle(TitlePart.TITLE, abortStartMessage)
    }

    fun start() {
        if (phase != GamePhase.STARTING && phase != GamePhase.LOBBY) return
        phase = GamePhase.RUNNING
        roleManager.start()
        taskManager.start()
        val gameSpawn = area.gameSpawn ?: throw IllegalStateException("Game spawn not set")
        val imposterTeamMatesMessage = textComponent {
            translatable("team.imposters.teammates") {
                args {
                    val imposterNames = players.filter { it.assignedRole?.definition?.team == Team.IMPOSTERS }
                        .map { Component.text(it.name, NamedTextColor.GOLD) }
                    val players = Component.join(
                        JoinConfiguration.separator(Component.text(", ", NamedTextColor.RED)),
                        imposterNames
                    )
                    component("players", players)
                }
            }
        }
        for (player in players) {
            player.editStatistics {
                statedGames.increment()
                playTime.timerStart()
            }
            player.player?.teleportAsync(gameSpawn)
            player.start()
            if (player.assignedRole?.definition?.team == Team.IMPOSTERS) {
                player.player?.sendMessage(imposterTeamMatesMessage)
            }
        }
        scoreboardManager.start()
    }

    fun checkWin() {
        if (!settings[SettingsKey.DEV.DO_WIN_CHECK]) return
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
        for (player in players) {
            val assignedRole = player.assignedRole ?: continue
            if (assignedRole.hasWon()) {
                letWin(assignedRole.definition.team)
                return
            }
        }
    }

    fun letWin(team: Team) {
        phase = GamePhase.FINISHED

        sabotageManager.endSabotage()
        invalidateAbilities()

        sendTitle(
            TitlePart.TITLE,
            textComponent {
                translatable("win.${team.id}")
            }
        )

        killManager.removeAllCorpses()
        taskManager.end()

        waypointManager.end()
        actionBarManager.end()
        scoreboardManager.end()

        roleManager.end()

        for (player in players) {
            if (cameraManager.isInCams(player)) cameraManager.leaveCams(player)
            if (ventManager.isVented(player)) ventManager.ventOut(player)

            val t = player.assignedRole?.definition?.team ?: Team.CREWMATES
            val hasWon = t === team

            player.editStatistics {
                if (player.isAlive) survivedGames.increment()
                if (hasWon) {
                    winsAs[player.assignedRole?.definition]?.increment()
                    winsWith[t]?.increment()
                } else {
                    losesAs[player.assignedRole?.definition]?.increment()
                    losesWith[t]?.increment()
                }
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
            player.restorePlayer()
            PlayerManager.gameEnds(player)
        }

        for (it in players.toList()) {
            removePlayer0(it)
        }

        EntityManager.dispose(this)
    }

    internal fun leavePlayer(amongUsPlayer: AmongUsPlayer, teleport: Boolean = true) {
        if (phase != GamePhase.LOBBY) return
        if (amongUsPlayer !in players) return
        removePlayer0(amongUsPlayer)
        if (teleport) amongUsPlayer.player?.teleport(amongUsPlayer.locationBeforeGame)
    }

    companion object {
        val MAX_DISCONNECT_TIME = 30.seconds
        const val DEFAULT_MAX_PLAYERS = 16
        const val NEEDED_PLAYERS_FOR_START = 4
        val CODE_CHARS = ('A'..'Z') + ('0'..'9')
        const val CODE_LENGTH = 4

        fun validCode(code: String) = code.length == CODE_LENGTH && code.all { it in CODE_CHARS }

        private fun createRandomCode(): String = (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")
    }
}