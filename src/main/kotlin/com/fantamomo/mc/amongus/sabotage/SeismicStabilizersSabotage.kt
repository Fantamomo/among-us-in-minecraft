package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.sabotage.SabotageType.SeismicStabilizers
import com.fantamomo.mc.amongus.util.Cooldown
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.block.data.type.Switch
import org.bukkit.entity.BlockDisplay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


class SeismicStabilizersSabotage(override val game: Game) :
    Sabotage<SeismicStabilizers, SeismicStabilizersSabotage> {
    override val sabotageType = SeismicStabilizers

    val seismicStabilizers1 = game.area.seismicStabilizers1?.block
        ?: throw IllegalStateException("Seismic stabilizers requires seismic stabilizers 1 position")
    val seismicStabilizers2 = game.area.seismicStabilizers2?.block
        ?: throw IllegalStateException("Seismic stabilizers requires seismic stabilizers 2 position")
    val seismicStabilizers1Particle =
        game.area.seismicStabilizers1Particle ?: game.area.seismicStabilizers1!!.toCenterLocation().add(0.0, 3.0, 0.0)
    val seismicStabilizers2Particle =
        game.area.seismicStabilizers2Particle ?: game.area.seismicStabilizers2!!.toCenterLocation().add(0.0, 3.0, 0.0)

    val seismicStabilizerDisplay1 = createDisplay(seismicStabilizers1)
    val seismicStabilizerDisplay2 = createDisplay(seismicStabilizers2)

    override val waypoints: Set<WaypointManager.Waypoint> = setOf(
        WaypointManager.Waypoint("sabotage.waypoint.seismic_stabilizers", Color.RED, seismicStabilizers1.location),
        WaypointManager.Waypoint("sabotage.waypoint.seismic_stabilizers", Color.RED, seismicStabilizers2.location)
    )

    private fun createDisplay(block: Block): BlockDisplay {
        return block.world.spawn(block.location, BlockDisplay::class.java) { display ->
            display.isVisibleByDefault = false
            display.block = block.blockData
            display.isGlowing = true
            display.glowColorOverride = Color.RED
            EntityManager.addEntityToRemoveOnStop(display)
        }
    }

    val timer = Cooldown(60.seconds)

    override fun start() {
        timer.reset(start = true)
        for (amongUsPlayer in game.players) {
            val player = amongUsPlayer.player ?: continue
            player.showEntity(AmongUs, seismicStabilizerDisplay1)
            player.showEntity(AmongUs, seismicStabilizerDisplay2)
        }
    }

    private var ticks = 0

    override fun tick() {
        if (timer.isFinished()) {
            game.sabotageManager.endSabotage()
            game.letWin(Team.IMPOSTERS)
            return
        }

        var ok = false

        (seismicStabilizers1.blockData as? Switch)?.let {
            seismicStabilizerDisplay1.block = it
            val powered = it.isPowered
            seismicStabilizerDisplay1.glowColorOverride = if (powered) Color.GREEN else Color.RED
            ok = powered
            if (ticks % 10 == 0) showParticle(seismicStabilizers1Particle.clone(), powered)
        }

        (seismicStabilizers2.blockData as? Switch)?.let {
            seismicStabilizerDisplay2.block = it
            val powered = it.isPowered
            seismicStabilizerDisplay2.glowColorOverride = if (powered) Color.GREEN else Color.RED
            ok = ok && powered
            if (ticks % 10 == 0) showParticle(seismicStabilizers2Particle.clone(), powered)
        }

        if (ok) game.sabotageManager.endSabotage()
        ticks++
    }

    private val particle = Particle.DUST.builder()
    private val maxHeight = 128

    private fun showParticle(location: Location, ok: Boolean = true) {
        particle.color(if (ok) Color.GREEN else Color.RED, 2.0f)
        particle.receivers(game.players.mapNotNull { it.player })
        while (location.y < maxHeight) {
            particle.location(location).spawn()
            location.add(0.0, ((location.y / maxHeight) * 2.75).coerceAtMost(0.7), 0.0)
        }
    }

    override fun stop(cause: SabotageStopCause) {
        timer.stop()
        for (amongUsPlayer in game.players) {
            val player = amongUsPlayer.player ?: continue
            player.hideEntity(AmongUs, seismicStabilizerDisplay1)
            player.hideEntity(AmongUs, seismicStabilizerDisplay2)
        }
    }

    override fun bossbarName() = textComponent {
        translatable("sabotage.bossbar.seismic_stabilizers") {
            args {
                string("time", timer.remaining().toString(DurationUnit.SECONDS, 0))
                numeric(
                    "count",
                    ((seismicStabilizers1.blockData as? Switch)?.isPowered
                        ?: (seismicStabilizers2.blockData as? Switch)?.isPowered)?.let { if (it) 1 else 0 } ?: 0)
            }
        }
    }

    override fun progress() =
        (timer.remaining().inWholeMilliseconds.toFloat() / timer.startDuration().inWholeMilliseconds.toFloat())
            .coerceIn(0.0f, 1.0f)
}