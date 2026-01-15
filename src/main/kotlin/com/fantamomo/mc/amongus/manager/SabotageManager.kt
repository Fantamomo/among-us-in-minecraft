package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.centerLocationOf
import com.fantamomo.mc.amongus.util.isBetween
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.type.Switch
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SabotageManager(private val game: Game) {

    private val cooldownBetweenSabotages =
        game.settings[SettingsKey.SABOTAGE_CRISIS_COOLDOWN].seconds

    private val cooldowns = mutableMapOf<String, Cooldown>()
    private var state: SabotageState = SabotageState.None

    val bossBar = BossBar.bossBar(
        Component.empty(),
        0f,
        BossBar.Color.RED,
        BossBar.Overlay.PROGRESS
    )

    private val lights = LightsSabotage(game)

    val supportedSabotages: Set<SabotageType> =
        setOfNotNull(SabotageType.Lights.takeIf { lights.isAvailable })

    /* ------------------- STATE ------------------- */

    fun currentSabotage(): SabotageType? = state.type
    fun isCurrentlySabotage() = state is SabotageState.Active
    fun isCrisis() = state.type?.isCrisis == true

    /* ------------------- COOLDOWN ------------------- */

    fun cooldown(type: SabotageType) =
        cooldowns.getOrPut(type.id) { Cooldown(10.seconds, true) }

    fun canSabotage(type: SabotageType): Boolean =
        type in supportedSabotages &&
                state is SabotageState.None &&
                cooldown(type).isFinished()

    /* ------------------- FLOW ------------------- */

    fun sabotage(type: SabotageType, ignoreCooldown: Boolean = false): Boolean {
        if (!ignoreCooldown && !canSabotage(type)) return false

        state = SabotageState.Active(type)

        game.sendTitle(TitlePart.TITLE, textComponent {
            translatable("sabotage.start.${type.id}")
        })

        when (type) {
            SabotageType.Lights -> lights.start()
            else -> {}
        }

        if (!ignoreCooldown) {
            cooldown(type).apply {
                set(cooldownBetweenSabotages)
                reset(start = true)
            }
        }

        updateBossBar()
        return true
    }

    fun endSabotage() {
        val type = state.type ?: return

        game.sendTitle(TitlePart.TITLE, textComponent {
            translatable("sabotage.end.${type.id}")
        })

        when (type) {
            SabotageType.Lights -> lights.end()
            else -> {}
        }

        state = SabotageState.None
        updateBossBar()
    }

    /* ------------------- BOSSBAR ------------------- */

    private fun updateBossBar() {
        val type = state.type
        if (type == null) {
            for (it in bossBar.viewers()) {
                bossBar.removeViewer(it as? Player ?: continue)
            }
            return
        }

        bossBar.name(Component.translatable("sabotage.bossbar.${type.id}"))
        game.players.mapNotNull { it.player }.forEach(bossBar::addViewer)
    }

    /* ------------------- LIGHT EVENTS ------------------- */

    fun onLightLeverFlip(location: Location) {
        if (state.type != SabotageType.Lights) return
        bossBar.progress(lights.progress)
        lights.onLeverFlip(location)
    }

    fun mayShowLightDisplayBlocks(player: AmongUsPlayer, location: Location) {
        if (state.type != SabotageType.Lights) return
        lights.mayUpdateLightDisplayBlocks(player, location)
    }

    sealed class SabotageState(val type: SabotageType?) {
        object None : SabotageState(null)
        class Active(type: SabotageType) : SabotageState(type)
    }

    inner class LightsSabotage(private val game: Game) {

        internal val min = game.area.lightPosMin
        internal val max = game.area.lightPosMax

        val waypoints: Set<WaypointManager.Waypoint> by lazy { SabotageType.Lights.getWaypoints(game) }

        val levers: List<Location> =
            if (min != null && max != null)
                game.area.lightLevers.filter { it.isBetween(min, max) }
            else emptyList()

        val isAvailable get() = levers.isNotEmpty()

        val progress: Float
            get() {
                val powered = levers.count {
                    (it.block.blockData as? Switch)?.isPowered == true
                }
                return (powered / levers.size.toFloat()).coerceIn(0f, 1f)
            }
        private val lightsBlockDisplays: Map<Block, BlockDisplay> = levers.map { it.block }.associateWith { block ->
            block.location.world.spawn(
                block.location,
                BlockDisplay::class.java
            ) { display ->
                val blockData = block.blockData
                display.block = blockData
                display.isVisibleByDefault = false
                display.isGlowing = true
                if (blockData is Switch) {
                    display.glowColorOverride = if (blockData.isPowered) Color.GREEN else Color.RED
                }
                EntityManager.addEntityToRemoveOnStop(display)
            }
        }
        private val lightsShownTo: MutableSet<AmongUsPlayer> = mutableSetOf()

        fun start() {
            bossBar.progress(0f)

            val random = Random

            lightsBlockDisplays.forEach { (block, display) ->
                val data = block.blockData as Switch
                val value = random.nextBoolean()
                data.isPowered = value
                block.blockData = data
                display.block = data
                display.glowColorOverride = if (value) Color.GREEN else Color.RED
            }

            if (progress == 1f) {
                val (lever, display) = lightsBlockDisplays.entries.random()
                val data = lever.blockData as Switch
                data.isPowered = false
                lever.blockData = data
                display.block = data
                display.glowColorOverride = Color.RED
            }

            game.players.forEach {
                if (!it.canSeeWhenLightsSabotage()) {
                    it.player?.addPotionEffect(
                        PotionEffect(
                            PotionEffectType.BLINDNESS,
                            PotionEffect.INFINITE_DURATION,
                            1,
                            false,
                            false,
                            false
                        )
                    )
                }
                for (waypoint in waypoints) {
                    game.waypointManager.assignWaypoint(it, waypoint)
                }
            }
        }

        fun end() {
            levers.forEach {
                val data = it.block.blockData as Switch
                data.isPowered = true
                it.block.blockData = data
            }

            game.players.forEach {
                for (display in lightsBlockDisplays.values) it.player?.hideEntity(AmongUs, display)
                it.player?.removePotionEffect(PotionEffectType.BLINDNESS)
                for (waypoint in waypoints) {
                    game.waypointManager.removeWaypoint(it, waypoint)
                }
            }
        }

        fun onLeverFlip(location: Location) {
            if (location !in levers) return
            val data = location.block.blockData as Switch
            data.isPowered = !data.isPowered
            val display = lightsBlockDisplays[location.block]
            if (display != null) {
                display.block = data
                display.glowColorOverride = if (data.isPowered) Color.GREEN else Color.RED
            }
            if (progress == 1f) {
                endSabotage()
                bossBar.progress(1f)
            } else {
                AmongUs.server.scheduler.runTask(AmongUs) { ->
                    val progress = progress
                    if (progress == 1f) {
                        endSabotage()
                    }
                    bossBar.progress(progress)
                }
            }
        }

        fun mayUpdateLightDisplayBlocks(amongUsPlayer: AmongUsPlayer, location: Location) {
            if (!isAvailable) return
            val min = min!!
            val max = max!!
            val player = amongUsPlayer.player ?: return
            if (location.isBetween(min, max)) {
                if (lightsShownTo.add(amongUsPlayer)) {
                    for (block in lightsBlockDisplays.values) {
                        player.showEntity(AmongUs, block)
                    }
                }
            } else {
                if (lightsShownTo.remove(amongUsPlayer)) {
                    for (block in lightsBlockDisplays.values) {
                        player.hideEntity(AmongUs, block)
                    }
                }
            }
        }
    }

    sealed class SabotageType(
        val id: String,
        val activeItem: Material,
        val deactivatedItem: Material = Material.BARRIER,
        val crisis: Boolean
    ) {
        object Lights : SabotageType(
            id = "lights",
            activeItem = Material.REDSTONE_TORCH,
            crisis = false
        ) {
            override fun getWaypoints(game: Game): Set<WaypointManager.Waypoint> {
                val min = game.sabotageManager.lights.min ?: return emptySet()
                val max = game.sabotageManager.lights.max ?: return emptySet()
                return setOf(
                    WaypointManager.Waypoint(
                        "sabotage.waypoint.lights",
                        Color.RED,
                        centerLocationOf(min, max)
                    )
                )
            }
        }

        object Communications :
            SabotageType(id = "communications", activeItem = Material.COMPASS, crisis = false)

        object ReactorMeltdown :
            SabotageType(id = "reactor_meltdown", activeItem = Material.END_CRYSTAL, crisis = true)

        object OxygenDepleted :
            SabotageType(id = "oxygen_depleted", activeItem = Material.END_CRYSTAL, crisis = true)

        object ResetSeismic :
            SabotageType(id = "reset_seismic", activeItem = Material.END_CRYSTAL, crisis = true)

        data class Door(val uuid: Uuid) :
            SabotageType(id = "door_$uuid", activeItem = Material.IRON_DOOR, crisis = false)

        val isCrisis: Boolean get() = crisis
        val isNonCrisis: Boolean get() = !crisis

        open fun getWaypoints(game: Game): Set<WaypointManager.Waypoint> = setOf()
    }
}
