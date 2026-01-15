package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.sabotage.SabotageType.SeismicStabilizers
import com.fantamomo.mc.amongus.util.Cooldown
import org.bukkit.Color
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

    override fun tick() {
        if (progress() == 1.0f) {
            game.sabotageManager.endSabotage()
            // todo: let imposter win
            return
        }
        var ok = false
        (seismicStabilizers1.blockData as? Switch)?.let {
            seismicStabilizerDisplay1.block = it
            val powered = it.isPowered
            seismicStabilizerDisplay1.glowColorOverride = if (powered) Color.GREEN else Color.RED
            ok = powered
        }
        (seismicStabilizers2.blockData as? Switch)?.let {
            seismicStabilizerDisplay2.block = it
            val powered = it.isPowered
            seismicStabilizerDisplay2.glowColorOverride = if (powered) Color.GREEN else Color.RED
            ok = ok && powered
        }
        if (ok) game.sabotageManager.endSabotage()
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
            }
        }
    }

    override fun progress() =
        (timer.remaining().inWholeMilliseconds.toFloat() / timer.startDuration().inWholeMilliseconds.toFloat())
            .coerceIn(0.0f, 1.0f)
}