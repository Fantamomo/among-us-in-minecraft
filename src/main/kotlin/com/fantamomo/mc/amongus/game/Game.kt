package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.manager.*
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.sabotage.SabotageManager
import com.fantamomo.mc.amongus.settings.Settings
import com.fantamomo.mc.amongus.task.TaskManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.DyeColor
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import kotlin.uuid.Uuid

class Game(
    area: GameArea,
    val world: World,
    val maxPlayers: Int = 16
) {
    val area: GameArea

    init {
        require(area.isValid()) { "Area ${area.name} is not valid" }
        this.area = area.withWorld(world)
    }

    val name: String get() = area.name
    val uuid: Uuid = Uuid.random()

    val settings: Settings = Settings()

    val ventManager = VentManager(this)
    val cameraManager = CameraManager(this)
    val waypointManager = WaypointManager(this)
    val actionBarManager = ActionBarManager(this)
    val sabotageManager = SabotageManager(this)
    val taskManager = TaskManager(this)
    val meetingManager = MeetingManager(this)

    internal val players = mutableListOf<AmongUsPlayer>()
    var phase: GamePhase = GamePhase.LOBBY

    var resultMessage: Component? = null

    fun addPlayer(player: Player): Boolean {
        if (phase != GamePhase.LOBBY) return false
        if (players.size >= maxPlayers) return false
        if (PlayerManager.exists(player.uniqueId)) return false
        PlayerManager.joinGame(player, this)
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
    }

    fun tick() {
        ventManager.tick()
        cameraManager.tick()
        waypointManager.tick()
        actionBarManager.tick()
        sabotageManager.tick()
        taskManager.tick()
        meetingManager.tick()
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
        if (phase.onDisconnectRemove) removePlayer0(player)
    }

    internal fun onRejoin(amongUsPlayer: AmongUsPlayer) {
        amongUsPlayer.mannequinController.getEntity()?.location?.let { amongUsPlayer.player?.teleport(it) }
    }

    fun sendChatMessage(component: Component) {
        players.forEach { it.player?.sendMessage(component) }
    }

    fun <T : Any> sendTitle(titlePart: TitlePart<T>, value: T) {
        players.forEach { it.player?.sendTitlePart(titlePart, value) }
    }
}