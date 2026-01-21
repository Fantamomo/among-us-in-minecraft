package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.manager.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.centerLocationOf
import com.fantamomo.mc.amongus.util.isBetween
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.data.type.Switch
import org.bukkit.entity.BlockDisplay
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import kotlin.random.Random

class LightsSabotage internal constructor(override val game: Game) :
    Sabotage<SabotageType.Lights, LightsSabotage> {
    override val sabotageType = SabotageType.Lights

    private val min =
        game.area.lightPosMin ?: throw IllegalStateException("Lights sabotage requires light min position")
    private val max =
        game.area.lightPosMax ?: throw IllegalStateException("Lights sabotage requires light max position")

    override val waypoints: Set<WaypointManager.Waypoint> = setOf(
        WaypointManager.Waypoint("sabotage.waypoint.lights", Color.RED, centerLocationOf(min, max))
    )
    val levers: Map<Block, BlockDisplay> =
        game.area.lightLevers.filter { it.isBetween(min, max) }.map { it.block }.associateWith(::createBlockDisplay)

    private fun createBlockDisplay(block: Block) =
        block.world.spawn(block.location, BlockDisplay::class.java) { display ->
            display.block = block.blockData
            display.isVisibleByDefault = false
            display.isGlowing = true
            EntityManager.addEntityToRemoveOnStop(display)
        }

    private val potionEffect = PotionEffect(
        PotionEffectType.BLINDNESS,
        PotionEffect.INFINITE_DURATION,
        1,
        false,
        false,
        false
    )

    override fun tick() {
        if (progress() == 1.0f) {
            game.sabotageManager.endSabotage()
        }
    }

    override fun start() {
        val random = Random.Default

        levers.forEach { (block, display) ->
            val data = block.blockData as Switch
            val value = random.nextBoolean()
            data.isPowered = value
            block.blockData = data
            display.block = data
            display.glowColorOverride = if (value) Color.GREEN else Color.RED
        }

        if (progress() == 1f) {
            val (lever, display) = levers.entries.random()
            val data = lever.blockData as Switch
            data.isPowered = false
            lever.blockData = data
            display.block = data
            display.glowColorOverride = Color.RED
        }

        game.players.forEach {
            if (!it.canSeeWhenLightsSabotage()) {
                it.player?.addPotionEffect(potionEffect)
            }
            for (waypoint in waypoints) {
                game.waypointManager.assignWaypoint(it, waypoint)
            }
        }
    }

    override fun stop(cause: SabotageStopCause) {
        pause()
    }

    override fun progress(): Float {
        var correct = 0
        for (block in levers.keys) {
            if (block.blockData is Switch && (block.blockData as Switch).isPowered) correct++
        }
        if (correct == 0) return 0f
        if (correct == levers.size) return 1f
        return correct.toFloat() / levers.size.toFloat()
    }

    override fun pause() {
        game.players.forEach {
            val player = it.player ?: return@forEach
            player.removePotionEffect(potionEffect.type)
            levers.values.forEach { display ->
                player.hideEntity(AmongUs, display)
            }
        }
    }

    override fun resume() {
        game.players.forEach {
            if (!it.canSeeWhenLightsSabotage()) {
                it.player?.addPotionEffect(potionEffect)
            }
        }
    }

    fun onLightLeverFlip(location: Location, amongUsPlayer: AmongUsPlayer): Boolean {
        val block = location.block
        if (block !in levers) return true
        if (!amongUsPlayer.isAlive) {
            amongUsPlayer.player?.run {
                sendTitlePart(TitlePart.SUBTITLE, Component.translatable("sabotage.subtitle.dead"))
                sendTitlePart(TitlePart.TITLE, Component.translatable("sabotage.title.dead"))
            }
            return false
        }
        val data = location.block.blockData as Switch
        data.isPowered = !data.isPowered
        val display = levers[block]
        if (display != null) {
            display.block = data
            display.glowColorOverride = if (data.isPowered) Color.GREEN else Color.RED
        }
        return true
    }

    private val doesSeeDisplays: MutableSet<UUID> = mutableSetOf()

    fun mayShowLightDisplayBlocks(amongUsPlayer: AmongUsPlayer, location: Location) {
        val player = amongUsPlayer.player ?: return
        if (location.isBetween(min, max)) {
            if (doesSeeDisplays.add(amongUsPlayer.uuid)) {
                levers.values.forEach { display ->
                    player.showEntity(AmongUs, display)
                }
            }
        } else {
            if (doesSeeDisplays.remove(amongUsPlayer.uuid)) {
                levers.values.forEach { display ->
                    player.hideEntity(AmongUs, display)
                }
            }
        }
    }
}