package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.ai.AiGameSummarizer
import com.fantamomo.mc.amongus.ai.AiService
import com.fantamomo.mc.amongus.ai.LobbyChatAiService
import com.fantamomo.mc.amongus.ai.MeetingAiService
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.*
import com.fantamomo.mc.amongus.manager.waypoint.WaypointManager
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.player.bot.BotName
import com.fantamomo.mc.amongus.player.bot.nav.NavGraph
import com.fantamomo.mc.amongus.player.bot.nav.NavGraphBuilder
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.role.RoleManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.util.WinCheckPhase
import com.fantamomo.mc.amongus.sabotage.SabotageManager
import com.fantamomo.mc.amongus.settings.Settings
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.BotsJoinMessages
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.audience.ListAudience
import com.fantamomo.mc.amongus.util.coroutines.ServerThread
import com.fantamomo.mc.amongus.util.internal.NMS
import com.fantamomo.mc.amongus.util.log.ActionLog
import com.fantamomo.mc.amongus.util.log.ActionLogManager
import com.fantamomo.mc.amongus.util.log.ActionLogUploader
import com.fantamomo.mc.amongus.util.log.elements.GameActionElements
import com.fantamomo.mc.amongus.util.log.elements.PlayerActionElements
import com.fantamomo.mc.amongus.util.sendComponent
import com.fantamomo.mc.amongus.util.toSmartString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.net.URI
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
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

    val navGraph: NavGraph

    init {
        require(area.isValid()) { "Area ${area.name} is not valid" }
        this.area = area.withWorld(world)
        logger.info("Game created with area ${area.name} in world ${world.name}")
    }

    val actionLog = ActionLog(
        uuid,
        mapOf(
            "area" to area.name,
            "world" to world.name,
            "max_players" to maxPlayers.toString(),
            "code" to code,
            "plugin" to mapOf(
                "version" to AmongUs.pluginMeta.version,
                "in_development" to AmongUsConstants.IN_DEVELOPMENT,
                "git_hash" to AmongUsConstants.GIT_HASH,
                "jar_type" to AmongUsConstants.JAR_TYPE.name.lowercase(),
                "unattached" to AmongUsConstants.UNATTACHED
            )
        )
    ).apply { ActionLogManager.register("game", this) }

    var host: HumanAmongUsPlayer? = null
        set(value) {
            if (value != null && value.game !== this) throw IllegalArgumentException("Player is not in this game")
            if (value != field) {
                actionLog.add(GameActionElements.HostChange(field?.uuid, value?.uuid))
            }
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
    val roleRevealManager = RoleRevealManager(this)
    val lobbyChatAiService = LobbyChatAiService(this)
    val meetingAiService = MeetingAiService(this)

    internal val players: MutableList<AmongUsPlayer> = mutableListOf()
    internal val bannedPlayers: MutableSet<UUID> = mutableSetOf()
    var phase: GamePhase = GamePhase.LOBBY
        internal set(value) {
            if (field == GamePhase.FINISHED) throw IllegalStateException("Cannot change phase to ${value.name} after game has finished")
            if (value != field) {
                actionLog.add(GameActionElements.PhaseChange(field, value))
            }
            field = value
            for (player in players) {
                if (player.isHuman && player.player != null) player.lastSeen = value
                else if (player.isBot) player.controller.onPhaseChange()
            }
        }

    var resultMessage: Component? = null

    val audienceAll = ListAudience.audienceHolder(::players)
    val audienceAlive = ListAudience.audienceHolder { players.filter { it.isAlive() } }
    val audienceDead = ListAudience.audienceHolder { players.filter { !it.isAlive() } }
    val audienceImposter =
        ListAudience.audienceHolder { players.filter { it.role.definition.team == Team.IMPOSTERS } }

    internal val audiences = listOf(audienceAll, audienceAlive, audienceDead, audienceImposter)

    private var botHasJoined = false
    private var lobbyChatAiServiceDownWarningSend = false

    init {
        navGraph = NavGraphBuilder(this).build()
    }

    fun addPlayer(player: Player, ignoreBanned: Boolean = false): Boolean {
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return false
        if (players.size >= maxPlayers) return false
        if (!ignoreBanned && player.uniqueId in bannedPlayers) return false
        if (PlayerManager.exists(player.uniqueId)) return false
        val newPlayer = PlayerManager.joinGame(player, this)
        scoreboardManager.addLobbyPlayer(newPlayer)
        actionLog.add(PlayerActionElements.PlayerJoin(player.uniqueId, AmongUsPlayerType.HUMAN))
        abortStartCooldown(GameActionElements.StartCountdownAborted.Reason.PLAYER_JOIN)
        audiences.forEach { it.setDirty() }
        logger.info("Adding player: ${player.name}")
        return true
    }

    fun addBot(name: BotName): Boolean {
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return false
        if (players.size >= maxPlayers) return false
        if (players.any { it.isBot && it.botName == name }) return false
        val bot = PlayerManager.addBot(name, this)
        if (!botHasJoined && AiService.isNotAvailable()) {
            audienceAll.sendComponent {
                translatable("game.ai_service_not_available")
            }
        }
        botHasJoined = true
        actionLog.add(PlayerActionElements.PlayerJoin(bot.uuid, AmongUsPlayerType.BOT))
        AmongUs.scope.launch {
            delay((10..500).random().milliseconds)
            val message = BotsJoinMessages.getRandomMessage(bot)
            withContext(Dispatchers.ServerThread) {
                if (players.contains(bot) && (phase == GamePhase.LOBBY || phase == GamePhase.STARTING)) {
                    chatManager.sendLobbyMessage(bot, Component.text(message), triggerAi = false, logMessage = false)
                }
            }
        }
        return true
    }

    fun removeBot(botName: BotName) {
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return
        val player = players.find { it.isBot && it.botName == botName }?.botOrNull ?: return
        val packet = ClientboundPlayerInfoRemovePacket(listOf(player.uuid))
        for (online in Bukkit.getOnlinePlayers()) {
            @Suppress("USELESS_ELVIS")
            val connection = (online as CraftPlayer).handle.connection ?: continue
            connection.send(packet)
        }

        removePlayer0(player)
        player.mannequinController.despawn()
        player.controller.entity.remove()
    }

    internal fun removePlayer0(player: AmongUsPlayer) {
        players.remove(player)
        actionLog.add(PlayerActionElements.PlayerRemove(player.uuid))
        if (player === host) {
            host = players.filterIsInstance<HumanAmongUsPlayer>().randomOrNull()
        }
        ventManager.removePlayer0(player)
        if (player.isHuman) {
            cameraManager.leaveCams(player)
            waypointManager.removePlayer(player)
            actionBarManager.removeAll(player)
            scoreboardManager.removePlayer(player)
        }
        sabotageManager.removePlayer(player)
        taskManager.removePlayer(player)
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
                player.mannequinController.syncFromOwner()
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

            if (!lobbyChatAiServiceDownWarningSend && AiService.isEnabled() && AiService.isNotAvailable()) {
                lobbyChatAiServiceDownWarningSend = true
                audienceAll.sendMessage(Component.translatable("game.ai_service_not_available"))
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
            player.internal
            try {
                player.tick(tickContext)
            } catch (e: Exception) {
                logger.error("Error while ticking player ${player.name}", e)
            }
            val disconnectedAt = (player as? HumanAmongUsPlayer)?.disconnectedAt ?: continue
            if (now - disconnectedAt < MAX_DISCONNECT_TIME) continue
            killPlayerDueDisconnect(player)
        }
    }

    private fun killPlayerDueDisconnect(player: HumanAmongUsPlayer) {
        killManager.kill(player, DeadReason.Disconnected, false)
        taskManager.removePlayer(player)
        player._abilities.clear()
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
    internal fun onDisconnected(player: HumanAmongUsPlayer) {
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

        actionLog.add(PlayerActionElements.PlayerDisconnect(player.uuid))

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
            if (player.isBot) return
            val p = player.player ?: continue
            val topInventory = p.openInventory.topInventory
            val holder = topInventory.holder as? WardrobeInventory ?: continue
            holder.update()
            for (entry in cooldowns) {
                p.setCooldown(entry.key.cooldownGroup, entry.value)
            }
        }
    }

    internal fun onRejoin(amongUsPlayer: HumanAmongUsPlayer) {
        actionLog.add(PlayerActionElements.PlayerRejoin(amongUsPlayer.uuid))
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
        roleRevealManager.onPlayerRejoin(amongUsPlayer)
        amongUsPlayer.modification?.onStart()
        audiences.forEach { it.setDirty() }
        if (!amongUsPlayer.isAlive()) amongUsPlayer.addGhostImprovements()
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
        actionLog.add(GameActionElements.StartCountdown)
        phase = GamePhase.STARTING
        startCooldownTicks = GameManager.currentTick.ticks + 200
        logger.info("Starting in 10 seconds")
    }

    fun abortStartCooldown(reason: GameActionElements.StartCountdownAborted.Reason) {
        if (phase != GamePhase.STARTING) return
        actionLog.add(
            GameActionElements.StartCountdownAborted(
                reason,
                ((startCooldownTicks - GameManager.currentTick.ticks) * 20).toInt()
            )
        )
        phase = GamePhase.LOBBY
        startCooldownTicks = -1
        sendTitle(TitlePart.TIMES, Title.DEFAULT_TIMES)
        val abortStartMessage = Component.translatable("game.start.aborted")
        sendTitle(TitlePart.TITLE, abortStartMessage)
        logger.info("Aborting start")
    }

    fun start() {
        if (phase != GamePhase.STARTING && phase != GamePhase.LOBBY) return
        if (area.gameSpawn == null) throw IllegalStateException("Game spawn is not set")
        if (area.lobbySpawn == null) throw IllegalStateException("Lobby spawn is not set")
        phase = GamePhase.REVEALING_ROLES
        roleManager.assign()
        chatManager.start()
        lobbyChatAiService.stop()

        for (player in players) {
            player.internal.preStart()
        }

        if (AmongUsDebug.DebugValues.SKIP_REVEAL_ROLE_PHASE.isEnabled()) {
            startGame()
        } else {
            roleRevealManager.start() // delegate to roleRevealManager.start()
        }
    }

    internal fun startGame() {
        if (phase != GamePhase.REVEALING_ROLES) return
        phase = GamePhase.RUNNING
        actionLog.add(GameActionElements.Start)
        logger.info("Game started")

        chatManager.clearChatHistory()
        roleManager.start()
        taskManager.start()

        val gameSpawn = area.gameSpawn ?: throw IllegalStateException("Game spawn is not set")
        val imposterTeamMatesMessage = textComponent {
            translatable("team.imposters.teammates") {
                args {
                    val imposterNames = players.filter { it.role.definition.team == Team.IMPOSTERS }
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
            player.internal

            player.humanOrNull?.editStatistics {
                statedGames.increment()
                playTime.timerStart()
            }
            player.teleportAsync(gameSpawn)
            player.start()
            if (player.assignedRole?.definition?.team == Team.IMPOSTERS) {
                player.audience.sendMessage(imposterTeamMatesMessage)
            }
        }
        scoreboardManager.start()
        audiences.forEach { it.setDirty() }
    }

    private fun checkRoleWins(phase: WinCheckPhase): Boolean {
        for (player in players) {
            val assignedRole = player.role
            if (assignedRole.winCheckPhase != phase) continue
            if (assignedRole.hasWon()) {
                letWin(assignedRole.definition.team)
                return true
            }
        }
        return false
    }

    fun checkWin() {
        if (!phase.isPlaying) return
        if (!settings[SettingsKey.DEV.DO_WIN_CHECK]) return
        logger.trace("Checking win")

        if (checkRoleWins(WinCheckPhase.PRE)) return

        if (taskManager.allTaskCompleted()) {
            letWin(Team.CREWMATES)
            return
        }

        if (checkRoleWins(WinCheckPhase.POST_TASK_CHECK)) return

        val alivePlayers = players.filter { it.isAlive() }
        val imposterCount = alivePlayers.count { it.role.definition.team == Team.IMPOSTERS }
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
        actionLog.add(GameActionElements.WinnerAnnouncement(team))
        phase = GamePhase.FINISHED

        logger.info("Game ended with $team win")

        val summarizer = AiGameSummarizer(this)
        summarizer.init()

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

        val playerList = players.toList()
        val bukkitPlayerList = playerList.mapNotNull { it.humanOrNull?.player }
        val hostPlayer = host?.player

        val toRemove = playerList.filter { it.isBot || it.player?.isOnline != true }.map { it.uuid }

        if (toRemove.isNotEmpty()) {
            val packet = ClientboundPlayerInfoRemovePacket(toRemove)
            for (online in Bukkit.getOnlinePlayers()) {
                @Suppress("USELESS_ELVIS")
                val connection = (online as CraftPlayer).handle.connection ?: continue
                connection.send(packet)
            }
        }

        for (player in playerList) {
            player.internal

            if (player.isInCams()) cameraManager.leaveCams(player)
            if (player.isVented()) ventManager.ventOut(player)

            val t = player.role.definition.team
            val hasWon = t === team

            player.humanOrNull?.editStatistics {
                if (player.isAlive()) survivedGames.increment()
                if (hasWon) {
                    winsAs[player.role.definition]?.increment()
                    winsWith[t]?.increment()
                } else {
                    losesAs[player.role.definition]?.increment()
                    losesWith[t]?.increment()
                }
                playTime.timerStop()
                playedGames.increment()
            }

            val subtitle = textComponent {
                if (hasWon) translatable("win.win") else translatable("win.lose")
            }
            val p = player.humanOrNull?.player
            if (p != null) {
                p.sendMessage(message)
                p.sendTitlePart(TitlePart.SUBTITLE, subtitle)
                for (online in Bukkit.getOnlinePlayers()) {
                    online.showPlayer(AmongUs, p)
                }
            }
            player.humanOrNull?.restorePlayer()
            PlayerManager.gameEnds(player)
        }

        for (it in playerList) {
            removePlayer0(it)
        }

        EntityManager.dispose(this)

        GameManager.gameEnd(this)
        actionLog.add(GameActionElements.End)

        if (AmongUsConfig.AI.generateGameSummary && AiService.isEnabled()) {
            AmongUs.scope.launch {
                try {
                    val (short, long) = summarizer.generate()
                    actionLog.customData["ai_long_summary"] = long
                    actionLog.customData["ai_short_summary"] = short
                    logger.info("Short summary: $short")
                    logger.info("Long summary: $long")
                    storeActionLog(bukkitPlayerList, hostPlayer)
                } catch (e: Exception) {
                    logger.error("Failed to generate AI summary", e)
                    storeActionLog(bukkitPlayerList, hostPlayer)
                }
            }
        } else {
            storeActionLog(bukkitPlayerList, hostPlayer)
        }
    }

    private fun storeActionLog(bukkitPlayerList: List<Player>, hostPlayer: Player?) {
        if (ActionLogUploader.enabled()) {
            val targets =
                if (AmongUsConfig.ActionLogUpload.sendToPlayers) bukkitPlayerList else listOfNotNull(hostPlayer)
            AmongUs.scope.launch {
                val uploadPath = ActionLogManager.saveUploadAndRemove(actionLog)
                if (uploadPath != null) {
                    if (uploadPath.host == "localhost") {
                        val uri = uploadPath.toURI()
                        for (target in targets) {
                            val url = URI(
                                uri.scheme,
                                uri.getUserInfo(),
                                target.virtualHost?.hostString,
                                uri.port,
                                uri.getPath(),
                                uri.getQuery(),
                                uri.getFragment()
                            ).toURL()
                            target.sendComponent {
                                translatable("action_log.uploaded") {
                                    args {
                                        component("url") {
                                            text(url.toString())
                                            hoverEvent(
                                                KHoverEventType.ShowText,
                                                Component.translatable("action_log.uploaded.hover")
                                            )
                                            clickEvent(KClickEventType.OpenUrl) {
                                                url(url)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val message = textComponent {
                            translatable("action_log.uploaded") {
                                args {
                                    component("url") {
                                        text(uploadPath.toString())
                                        hoverEvent(
                                            KHoverEventType.ShowText,
                                            Component.translatable("action_log.uploaded.hover")
                                        )
                                        clickEvent(KClickEventType.OpenUrl) {
                                            url(uploadPath)
                                        }
                                    }
                                }
                            }
                        }
                        targets.forEach { it.sendMessage(message) }
                    }
                    logger.info("Uploaded action log to $uploadPath")
                } else {
                    val err = Component.translatable("action_log.upload_failed")
                    targets.forEach { it.sendMessage(err) }
                    logger.warn("Failed to upload action log")
                }
            }
        } else {
            ActionLogManager.saveAndRemove(actionLog)
        }
    }

    private fun getWinMessage(team: Team): Component = textComponent {
        val winners = players.filter { it.role.definition.team == team }
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
            val alive = player.isAlive()
            val color = player.role.definition.team.textColor
            val deadReason = player.deadReason
            newLine()
            translatable(if (alive) "win.message.player.alive" else "win.message.player.dead") {
                args {
                    component("player", Component.text(player.name, color))
                    component("role") {
                        val assignedRole = player.role
                        append(assignedRole.definition.name)
                        hoverEvent(KHoverEventType.ShowText) {
                            append(assignedRole.definition.descriptionOther)
                            assignedRole.gameEndInfo()?.let { message ->
                                newLine()
                                newLine()
                                append(message)
                            }
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
        actionLog.add(PlayerActionElements.PlayerLeave(amongUsPlayer.uuid))
        if (teleport && amongUsPlayer.isHuman) {
            val future = amongUsPlayer.teleportAsync(amongUsPlayer.locationBeforeGame).thenAccept {
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