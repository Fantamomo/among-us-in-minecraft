package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.color
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.ActionBarManager
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.accuracyToColor
import com.fantamomo.mc.amongus.util.getAimAccuracy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.entity.BlockDisplay
import kotlin.math.roundToInt
import kotlin.random.Random

class CommunicationsSabotage(
    override val game: Game
) : Sabotage<SabotageType.Communications, CommunicationsSabotage> {

    override val sabotageType = SabotageType.Communications

    val position = game.area.communications
        ?: throw IllegalStateException("Communications sabotage requires communications position")

    override val waypoints: Set<WaypointManager.Waypoint> = setOf(
        WaypointManager.Waypoint("sabotage.waypoint.communications", Color.RED, position)
    )

    private val fixingPlayers = mutableSetOf<FixingPlayer>()

    private val display = position.world.spawn(position, BlockDisplay::class.java) {
        it.block = position.block.blockData
        it.isVisibleByDefault = false
        it.isGlowing = true
        it.glowColorOverride = Color.RED
        EntityManager.addEntityToRemoveOnStop(it)
    }

    private val name = textComponent {
        translatable("sabotage.bossbar.communications")
    }

    override fun start() = resume()

    override fun stop(cause: SabotageStopCause) = pause()

    override fun tick() {
        fixingPlayers.forEach { it.tick() }
    }

    override fun progress() = 1.0f

    override fun bossbarName() = name

    override fun pause() {
        game.players.forEach { it.player?.hideEntity(AmongUs, display) }
        fixingPlayers.forEach(FixingPlayer::dispose)
        fixingPlayers.clear()
    }

    override fun resume() {
        game.players.forEach { it.player?.showEntity(AmongUs, display) }
    }

    fun onPlayerInteract(player: AmongUsPlayer) {
        if (fixingPlayers.any { it.player == player }) return
        fixingPlayers.add(FixingPlayer(player))
    }

    fun removePlayer(player: AmongUsPlayer) {
        val fixing = fixingPlayers.find { it.player == player } ?: return
        fixing.dispose()
        fixingPlayers.remove(fixing)
    }

    inner class FixingPlayer(val player: AmongUsPlayer) {

        private val targetYaw = Random.nextInt(360).toFloat()
        private val targetPitch = Random.nextInt(-90, -15).toFloat()

        private var lockProgress = 0f
        private val lockTime = 3.0f

        private val filledChar = "▌"
        private val emptyChar = "▁"

        private val actionBar = game.actionBarManager.part(
            player,
            "sabotage/communications",
            ActionBarManager.ActionBarPartType.CENTER,
            200
        )

        private var tick = 0

        private fun accuracy(): Float {
            val loc = player.player?.location ?: return 0f
            return getAimAccuracy(
                loc.yaw,
                loc.pitch,
                targetYaw,
                targetPitch,
                0.75f
            )
        }

        fun tick() {
            val acc = accuracy()
            var color = TextColor.color(accuracyToColor(acc).asRGB())

            val isLockedOn = acc > 0.99f

            // Lock-Fortschritt
            if (isLockedOn) {
                lockProgress += 0.05f
            } else {
                lockProgress = (lockProgress - 0.1f).coerceAtLeast(0f)
            }

            val accuracyPercent = (acc * 100).roundToInt().coerceIn(0, 100)
            val lockPercent = ((lockProgress / lockTime) * 100).roundToInt().coerceIn(0, 100)

            val barValue = if (isLockedOn) lockPercent else accuracyPercent
            val barFilled = (barValue / 5).coerceIn(0, 20)

            val bar =
                filledChar.repeat(barFilled) +
                        emptyChar.repeat(20 - barFilled)

            val status = when {
                lockProgress >= lockTime -> LOCKED
                isLockedOn -> HOLDING
                acc > 0.8f -> STABILIZING
                acc > 0.5f -> SEARCHING
                else -> NO_SIGNAL
            }

            if (lockProgress >= lockTime && tick % 6 < 3) {
                color = NamedTextColor.WHITE
            } else if (isLockedOn) {
                color = NamedTextColor.LIGHT_PURPLE
            }

            actionBar.componentLike = textComponent {
                translatable("sabotage.actionbar.communications") {
                    args {
                        component("status", status)
                        string("bar", bar)
                        numeric("percent", barValue)
                    }
                    color(color)
                }
            }
//                Component.text("📡 $status $bar $barValue%")
//                    .color(TextColor.color(color.asRGB()))

            if (lockProgress >= lockTime) {
                AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
                    if (lockProgress >= lockTime && acc > 0.99f) {
                        game.sabotageManager.endSabotage()
                    }
                }, 40L)
            }

            tick++
        }

        fun dispose() {
            actionBar.remove()
        }
    }

    companion object {
        private val LOCKED = Component.translatable("sabotage.actionbar.communications.locked")
        private val HOLDING = Component.translatable("sabotage.actionbar.communications.holding")
        private val STABILIZING = Component.translatable("sabotage.actionbar.communications.stabilizing")
        private val SEARCHING = Component.translatable("sabotage.actionbar.communications.searching")
        private val NO_SIGNAL = Component.translatable("sabotage.actionbar.communications.no_signal")
    }
}
