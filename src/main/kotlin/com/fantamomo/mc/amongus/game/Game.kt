package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.*
import com.fantamomo.mc.amongus.manager.waypoint.WaypointManager
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.role.RoleManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.util.WinCheckPhase
import com.fantamomo.mc.amongus.sabotage.SabotageManager
import com.fantamomo.mc.amongus.settings.Settings
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.audience.ListAudience
import com.fantamomo.mc.amongus.util.internal.NMS
import com.fantamomo.mc.amongus.util.toSmartString
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class Game(
    area: GameArea,
    val world: World,
    val maxPlayers: Int = DEFAULT_MAX_PLAYERS
) {
    val area: GameArea

    val uuid: Uuid = Uuid.random()
    val code: String = createRandomCode()

    val logger = ComponentLogger.logger("Among Us: $code")

    init {
        require(area.isValid()) { "Area ${area.name} is not valid" }
        this.area = area.withWorld(world)
        logger.info("Game created with area ${area.name} in world ${world.name}")
    }

    var host: AmongUsPlayer? = null
        set(value) {
            if (value != null && value.game !== this) throw IllegalArgumentException("Player is not in this game")
            field = value
        }

    private var lastPlayer: Long = -1

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

    internal val players: MutableList<AmongUsPlayer> = mutableListOf()
    internal val bannedPlayers: MutableSet<UUID> = mutableSetOf()
    var phase: GamePhase = GamePhase.LOBBY

    var resultMessage: Component? = null

    val audienceAll = ListAudience.audienceHolder(::players)
    val audienceAlive = ListAudience.audienceHolder { players.filter { it.isAlive } }
    val audienceDead = ListAudience.audienceHolder { players.filter { !it.isAlive } }
    val audienceImposter = ListAudience.audienceHolder { players.filter { it.assignedRole?.definition?.team == Team.IMPOSTERS } }

    internal val audiences = listOf(audienceAll, audienceAlive, audienceDead, audienceImposter)

    fun addPlayer(player: Player, ignoreBanned: Boolean = false): Boolean {
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return false
        if (players.size >= maxPlayers) return false
        if (!ignoreBanned && player.uniqueId in bannedPlayers) return false
        if (PlayerManager.exists(player.uniqueId)) return false
        val newPlayer = PlayerManager.joinGame(player, this)
        scoreboardManager.addLobbyPlayer(newPlayer)
        abortStartCooldown()
        audiences.forEach { it.setDirty() }
        logger.info("Adding player: ${player.name}")
        return true
    }

    internal fun removePlayer0(player: AmongUsPlayer) {
        players.remove(player)
        if (player === host) {
            host = players.randomOrNull()
        }
        ventManager.removePlayer0(player)
        cameraManager.leaveCams(player)
        waypointManager.removePlayer(player)
        actionBarManager.removeAll(player)
        sabotageManager.removePlayer(player)
        taskManager.removePlayer(player)
        scoreboardManager.removePlayer(player)
        morphManager.removePlayer(player)
        audiences.forEach { it.setDirty() }
    }

    fun tick(tickContext: TickContext) {
        logger.trace("Ticking: ${tickContext.ticks}")
        if (world.playerCount == 0) {
            val currentTimeMillis = System.currentTimeMillis()
            if (lastPlayer == -1L) lastPlayer = currentTimeMillis
            if (lastPlayer + 300000 < currentTimeMillis) { // 300000 = 5 minutes
                logger.info("Removing due that there are no players")
                GameManager.markForRemove(this)
                return
            }
        } else {
            lastPlayer = -1L
        }
        if (phase == GamePhase.LOBBY || phase == GamePhase.STARTING) {
            for (player in players) {
                player.mannequinController.syncFromPlayer()
            }
            scoreboardManager.tick()

            if (startCooldownTicks > tickContext.ticks) {
                val remainingTicks = startCooldownTicks - tickContext.ticks
                val remaining = (remainingTicks + 19) / 20

                val color = when {
                    remaining <= 3 -> NamedTextColor.DARK_RED
                    remaining <= 5 -> NamedTextColor.RED
                    else -> NamedTextColor.GOLD
                }

                val title = Title.title(
                    Component.text(remaining.toString())
                        .color(color)
                        .decorate(TextDecoration.BOLD),
                    Component.empty(),
                    0,
                    20,
                    0
                )

                audienceAll.showTitle(title)
            } else if (startCooldownTicks == tickContext.ticks) {
                startCooldownTicks = -1
                start()
            }

            return
        }
        if (phase == GamePhase.FINISHED) return
        tickContext.every(20) {
            if (settings[SettingsKey.DEV.DO_WIN_CHECK_ON_TICK]) checkWin()
        }
        ventManager.tick(tickContext)
        cameraManager.tick(tickContext)
        waypointManager.tick()
        actionBarManager.tick()
        sabotageManager.tick(tickContext)
        taskManager.tick()
        meetingManager.tick()
        scoreboardManager.tick()
        roleManager.tick(tickContext)
        morphManager.tick()
        ghostFormManager.tick()

        val now = Clock.System.now()

        for (player in players) {
            player.player?.saturation = 5.0f
            player.player?.foodLevel = 20
            player.modification?.onTick(tickContext)
            player.mannequinController.syncFromPlayer()
            val disconnectedAt = player.disconnectedAt ?: continue
            if (now - disconnectedAt < MAX_DISCONNECT_TIME) continue
            killPlayer(player)
        }
    }

    private fun killPlayer(player: AmongUsPlayer) {
        killManager.kill(player, DeadReason.Disconnected, false)
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

    @NMS
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

        sabotageManager.onDisconnect(player)

        if (phase.onDisconnectRemove) {
            val packet = ClientboundPlayerInfoRemovePacket(listOf(player.uuid))
            for (online in Bukkit.getOnlinePlayers()) {
                @Suppress("USELESS_ELVIS")
                val connection = (online as CraftPlayer).handle.connection ?: continue
                connection.send(packet)
            }
            removePlayer0(player)
        }
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
        sabotageManager.onPlayerRejoin(amongUsPlayer)
        taskManager.onPlayerRejoin(amongUsPlayer)
        amongUsPlayer.modification?.onStart()
        audiences.forEach { it.setDirty() }
        if (!amongUsPlayer.isAlive) amongUsPlayer.addGhostImprovements()
    }

    fun sendChatMessage(component: Component) {
        audienceAll.sendMessage(component)
    }

    fun <T : Any> sendTitle(titlePart: TitlePart<T>, value: T) {
        audienceAll.sendTitlePart(titlePart, value)
    }

    fun invalidateAbilities() {
        AbilityManager.invalidateAll(this)
    }

    internal var startCooldownTicks: Long = -1
        private set

    fun startStartCooldown() {
        if (phase != GamePhase.LOBBY) return
        phase = GamePhase.STARTING
        startCooldownTicks = GameManager.currentTick.ticks + 200
        logger.info("Starting in 10 seconds")
    }

    fun abortStartCooldown() {
        if (phase != GamePhase.STARTING) return
        phase = GamePhase.LOBBY
        startCooldownTicks = -1
        sendTitle(TitlePart.TIMES, Title.DEFAULT_TIMES)
        val abortStartMessage = Component.translatable("game.start.aborted")
        sendTitle(TitlePart.TITLE, abortStartMessage)
        logger.info("Aborting start")
    }

    fun start() {
        if (phase != GamePhase.STARTING && phase != GamePhase.LOBBY) return
        phase = GamePhase.RUNNING
        logger.info("Game started")
        roleManager.start()
        taskManager.start()
        chatManager.start()
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
        audiences.forEach { it.setDirty() }
    }

    private fun checkRoleWins(phase: WinCheckPhase): Boolean {
        for (player in players) {
            val assignedRole = player.assignedRole ?: continue
            if (assignedRole.winCheckPhase != phase) continue
            if (assignedRole.hasWon()) {
                letWin(assignedRole.definition.team)
                return true
            }
        }
        return false
    }

    fun checkWin() {
        logger.trace("Checking win")
        if (!settings[SettingsKey.DEV.DO_WIN_CHECK]) return

        if (checkRoleWins(WinCheckPhase.PRE)) return

        if (taskManager.allTaskCompleted()) {
            letWin(Team.CREWMATES)
            return
        }

        if (checkRoleWins(WinCheckPhase.POST_TASK_CHECK)) return

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

        if (checkRoleWins(WinCheckPhase.POST)) return
    }

    @NMS
    fun letWin(team: Team) {
        if (phase == GamePhase.FINISHED) return
        phase = GamePhase.FINISHED

        logger.info("Game ended with $team win")

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

        val message = getWinMessage(team)
        resultMessage = message

        val toRemove = players.filter { it.player?.isOnline != true }.map { it.uuid }

        if (toRemove.isNotEmpty()) {
            val packet = ClientboundPlayerInfoRemovePacket(toRemove)
            for (online in Bukkit.getOnlinePlayers()) {
                @Suppress("USELESS_ELVIS")
                val connection = (online as CraftPlayer).handle.connection ?: continue
                connection.send(packet)
            }
        }

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
                p.sendMessage(message)
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

        GameManager.gameEnd(this)
    }

    private fun getWinMessage(team: Team): Component = textComponent {
        val winners = players.filter { it.assignedRole?.definition?.team == team }
        repeat(5) { newLine() }
        translatable("win.${team.id}")
        newLine()
        translatable("win.message.winners") {
            args {
                component(
                    "players",
                    Component.join(
                        JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)),
                        winners.map { Component.text(it.name, NamedTextColor.GOLD) }
                    )
                )
            }
        }
        newLine()
        for (player in players) {
            val alive = player.isAlive
            val color = player.assignedRole?.definition?.team?.textColor ?: NamedTextColor.WHITE
            val deadReason = player.deadReason
            newLine()
            translatable(if (alive) "win.message.player.alive" else "win.message.player.dead") {
                args {
                    component("player", Component.text(player.name, color))
                    component("role") {
                        val assignedRole = player.assignedRole
                        if (assignedRole != null) {
                            append(assignedRole.definition.name)
                            hoverEvent(KHoverEventType.ShowText) {
                                append(assignedRole.definition.descriptionOther)
                                assignedRole.gameEndInfo()?.let { message ->
                                    newLine()
                                    newLine()
                                    append(message)
                                }
                            }
                        } else {
                            translatable("scoreboard.role.none")
                        }
                    }
                    if (deadReason != null) component("reason", deadReason.name)
                }
            }
        }
    }

    internal fun leavePlayer(amongUsPlayer: AmongUsPlayer, teleport: Boolean = true): Boolean {
        if (phase != GamePhase.LOBBY) return true
        if (amongUsPlayer !in players) return true
        players.remove(amongUsPlayer)
        if (teleport) {
            val future = amongUsPlayer.player?.teleportAsync(amongUsPlayer.locationBeforeGame)?.thenAccept {
                LastPlayerLocationManager.remove(amongUsPlayer.uuid.toKotlinUuid())
                removePlayer0(amongUsPlayer)
                amongUsPlayer.player = null
            }
            if (future == null) removePlayer0(amongUsPlayer)
            return future == null
        } else removePlayer0(amongUsPlayer)
        return true
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