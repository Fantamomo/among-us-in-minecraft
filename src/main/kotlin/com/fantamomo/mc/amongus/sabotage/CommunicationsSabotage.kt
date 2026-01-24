package com.fantamomo.mc.amongus.sabotage

import com.destroystokyo.paper.ParticleBuilder
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
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.TitlePart
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random


class CommunicationsSabotage(
    override val game: Game
) : Sabotage<SabotageType.Communications, CommunicationsSabotage> {

    override val sabotageType = SabotageType.Communications

    val position = game.area.communications
        ?: error("Communications sabotage requires communications position")
    val outgoingBeam = game.area.outgoingCommunicationBeam

    override val waypoints = setOf(
        WaypointManager.Waypoint(
            "sabotage.waypoint.communications",
            Color.RED,
            position
        )
    )

    private val fixingPlayers = mutableSetOf<FixingPlayer>()
    private var fixedPlayer: FixingPlayer? = null

    private val display: BlockDisplay = position.world.spawn(position, BlockDisplay::class.java) {
        it.block = position.block.blockData
        it.isVisibleByDefault = false
        it.isGlowing = true
        it.glowColorOverride = Color.RED
        EntityManager.addEntityToRemoveOnEnd(game, it)
    }

    private val bossBarName = textComponent {
        translatable("sabotage.bossbar.communications")
    }

    override fun start() {
        game.taskManager.updateBossbar()
        resume()
    }

    override fun stop(cause: SabotageStopCause) {
        game.taskManager.updateBossbar()
//        val fixedPlayer = fixedPlayer
//        if (fixedPlayer != null && outgoingBeam != null) {
//            val clone = outgoingBeam.clone()
//            clone.yaw = fixedPlayer.targetYaw
//            clone.pitch = fixedPlayer.targetPitch
//            shootParticleBeam(clone)
//        }
        pause()
    }

    private val particle = ParticleBuilder(Particle.DUST)
        .color(Color.FUCHSIA, 2.0f)

    private fun shootParticleBeam(
        start: Location,
        player: Player,
        color: Color
    ) {
        val particle = ParticleBuilder(Particle.DUST)
            .color(color, 1.8f)
            .receivers(player)

        val direction = start.getDirection().normalize()
        val maxDistance = 75.0
        val step = 0.5

        var d = 0.0
        while (d <= maxDistance) {
            val loc = start.clone().add(direction.clone().multiply(d))
            particle.location(loc).spawn()
            d += step
        }
    }


    private fun computeAimingLocation(start: Location, playerEye: Location, length: Int): Location {
        playerEye.yaw -= 180
        if (playerEye.yaw < 0) playerEye.yaw += 360
        playerEye.pitch = -playerEye.pitch

        val eye = playerEye.toVector()
        val dir = playerEye.getDirection().normalize()

        val center = start.toVector()
        val radius = length.toDouble()

        val oc = eye.subtract(center)
        val a = dir.dot(dir)
        val b = 2.0 * oc.dot(dir)
        val c = oc.dot(oc) - radius * radius

        val discriminant = b * b - 4 * a * c
        if (discriminant < 0) {
            return start.clone().apply { setDirection(playerEye.toVector().subtract(start.toVector())) }
        }

        val t = (-b - sqrt(discriminant)) / (2 * a)
        val targetPoint = playerEye.toVector().add(dir.multiply(t))
        val directionFromStart = targetPoint.subtract(start.toVector()).normalize()

        return start.clone()
            .apply { setDirection(directionFromStart) }
    }

    override fun tick() {
        fixingPlayers.forEach(FixingPlayer::tick)
    }

    override fun progress(): Float = 1.0f

    override fun bossbarName(): Component = bossBarName

    override fun pause() {
        game.players.forEach { it.player?.hideEntity(AmongUs, display) }
        fixingPlayers.forEach(FixingPlayer::dispose)
        fixingPlayers.clear()
    }

    override fun resume() {
        game.players.forEach { it.player?.showEntity(AmongUs, display) }
    }

    fun onPlayerInteract(player: AmongUsPlayer) {
        if (!player.isAlive) {
            player.player?.run {
                sendTitlePart(TitlePart.SUBTITLE, Component.translatable("sabotage.subtitle.dead"))
                sendTitlePart(TitlePart.TITLE, Component.translatable("sabotage.title.dead"))
            }
            return
        }
        if (fixingPlayers.any { it.player == player }) return
        fixingPlayers += FixingPlayer(player)
    }

    fun canMoveAndDisable(player: AmongUsPlayer): Boolean {
        val fixingPlayer = fixingPlayers.firstOrNull { it.player == player } ?: return false
        return fixingPlayer.canMoveOnce.also { fixingPlayer.canMoveOnce = false }
    }

    fun removePlayer(player: AmongUsPlayer) {
        fixingPlayers.firstOrNull { it.player == player }?.let {
            it.dispose()
            fixingPlayers -= it
        }
    }

    inner class FixingPlayer(val player: AmongUsPlayer) {

        var canMoveOnce: Boolean = true
        val targetYaw = Random.nextFloat() * 360f
        val targetPitch = Random.nextDouble(-90.0, -15.0).toFloat()

        private var lockProgress = 0f
        private var tickCounter = 0
        private var lastBeamLocation: Pair<Pair<Float, Float>, Location>? = null

        private val actionBar = game.actionBarManager.part(
            player,
            "sabotage/communications",
            ActionBarManager.ActionBarPartType.CENTER,
            200
        )

        private fun aimAccuracy(): Float {
            val location = player.player?.location ?: return 0f
            return getAimAccuracy(
                location.yaw,
                location.pitch,
                targetYaw,
                targetPitch,
                AIM_TOLERANCE
            )
        }

        fun tick() {
            val player = player.player ?: return
            val rotation = player.location.run { yaw to pitch }
            val accuracy = aimAccuracy()
            val lockedOn = accuracy >= LOCK_THRESHOLD

            lockProgress = when {
                lockedOn -> lockProgress + LOCK_INCREASE
                else -> (lockProgress - LOCK_DECREASE).coerceAtLeast(0f)
            }

            val accuracyPercent = (accuracy * 100).roundToInt().coerceIn(0, 100)
            val lockPercent = ((lockProgress / LOCK_TIME) * 100)
                .roundToInt()
                .coerceIn(0, 100)

            val barValue = if (lockedOn) lockPercent else accuracyPercent
            val filledBars = (barValue / 5).coerceIn(0, BAR_LENGTH)

            val bar = BAR_FILLED.repeat(filledBars) +
                    BAR_EMPTY.repeat(BAR_LENGTH - filledBars)

            val status = when {
                lockProgress >= LOCK_TIME -> LOCKED
                lockedOn -> HOLDING
                accuracy > 0.8f -> STABILIZING
                accuracy > 0.5f -> SEARCHING
                else -> NO_SIGNAL
            }

            val color = when {
                lockProgress >= LOCK_TIME && tickCounter % 6 < 3 ->
                    Color.WHITE

                lockedOn ->
                    Color.FUCHSIA

                else ->
                    accuracyToColor(accuracy)
            }

            actionBar.componentLike = textComponent {
                translatable("sabotage.actionbar.communications") {
                    args {
                        component("status", status)
                        string("bar", bar)
                        numeric("percent", barValue)
                    }
                    color(TextColor.color(color.asRGB()))
                }
            }

            if (outgoingBeam != null && player.location.pitch <= -10) {
                val last = lastBeamLocation
                if (last == null || last.first != rotation) {
                    val beamAimPoint = computeAimingLocation(outgoingBeam, player.eyeLocation, 75)
                    shootParticleBeam(beamAimPoint, player, color)
                    lastBeamLocation = rotation to beamAimPoint
                } else if (tickCounter % 15 == 0) {
                    shootParticleBeam(last.second, player, color)
                }
            }

            if (lockProgress >= LOCK_TIME) {
                scheduleSabotageFinish()
            }

            tickCounter++
        }

        private fun scheduleSabotageFinish() {
            AmongUs.server.scheduler.runTaskLater(
                AmongUs,
                { ->
                    if (lockProgress >= LOCK_TIME && aimAccuracy() >= LOCK_THRESHOLD) {
                        if (game.sabotageManager.currentSabotage() === this@CommunicationsSabotage) {
                            fixedPlayer = this
                            game.sabotageManager.endSabotage()
                        }
                    }
                },
                FINISH_DELAY_TICKS
            )
        }

        fun dispose() {
            actionBar.remove()
        }
    }

    companion object {
        private const val LOCK_TIME = 3.0f
        private const val LOCK_INCREASE = 0.05f
        private const val LOCK_DECREASE = 0.1f
        private const val LOCK_THRESHOLD = 0.99f
        private const val AIM_TOLERANCE = 0.5f
        private const val BAR_LENGTH = 20
        private const val FINISH_DELAY_TICKS = 40L

        private const val BAR_FILLED = "▌"
        private const val BAR_EMPTY = "▁"

        private val LOCKED = Component.translatable("sabotage.actionbar.communications.locked")
        private val HOLDING = Component.translatable("sabotage.actionbar.communications.holding")
        private val STABILIZING = Component.translatable("sabotage.actionbar.communications.stabilizing")
        private val SEARCHING = Component.translatable("sabotage.actionbar.communications.searching")
        private val NO_SIGNAL = Component.translatable("sabotage.actionbar.communications.no_signal")
    }
}
