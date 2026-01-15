package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.isBetween
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.empty
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
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

    val minLights = game.area.lightPosMin
    val maxLights = game.area.lightPosMax

    val lightLevers =
        if (minLights != null && maxLights != null)
            game.area.lightLevers.filter { it.isBetween(minLights, maxLights) && it.block.blockData is Switch }
        else emptyList()

    val lightsBlockDisplays: Map<Block, BlockDisplay> = lightLevers.map { it.block }.associateWith { block ->
        block.location.world.spawn(block.location, BlockDisplay::class.java) { display ->
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
    val playerWhoSeeLeverDisplays = mutableSetOf<AmongUsPlayer>()

    val supportedSabotages: Set<SabotageType> = setOfNotNull(
        SabotageType.Lights.takeIf { lightLevers.isNotEmpty() }
    )

    val bossBar = BossBar.bossBar(empty(), 0.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)

    private var activeSabotage: SabotageType? = null
    private val cooldowns = mutableMapOf<String, Cooldown>()

    fun currentSabotage(): SabotageType? = activeSabotage

    fun isCurrentlySabotage(): Boolean =
        activeSabotage != null

    fun isCrisis(): Boolean =
        activeSabotage?.isCrisis == true

    fun isNonCrisis(): Boolean =
        activeSabotage?.isNonCrisis == true

    fun getCooldown(type: SabotageType) = cooldowns.getOrPut(type.id) {
        Cooldown(10.seconds, true)
    }

    fun canSabotage(type: SabotageType): Boolean {
        if (type !in supportedSabotages) return false
        if (isCurrentlySabotage()) return false
        return getCooldown(type).isFinished()
    }

    fun sabotage(type: SabotageType, ignoreCooldown: Boolean = false): Boolean {
        if (!ignoreCooldown && !canSabotage(type)) return false

        activeSabotage = type

        type.startSabotage(this)

        if (!ignoreCooldown) {
            val cooldown = getCooldown(type)
            cooldown.set(cooldownBetweenSabotages)
            cooldown.reset(start = true)
        }

        updateBossBar()

        return true
    }

    private fun updateBossBar() {
        val activeSabotage = activeSabotage
        if (activeSabotage != null) {
            bossBar.name(Component.translatable("sabotage.bossbar." + activeSabotage.id))
            for (player in game.players) {
                player.player?.showBossBar(bossBar)
            }
        } else {
            bossBar.name(empty())
            bossBar.viewers().forEach {
                bossBar.removeViewer(it as Player)
            }
        }
    }

    fun endSabotage() {
        val sabotage = activeSabotage ?: return

        sabotage.endSabotage(this)
        this.activeSabotage = null
        updateBossBar()
    }

    private fun startLights() {
        val switches = lightLevers.map { it.block }.filter { it.blockData is Switch }
        if (switches.isEmpty()) {
            endSabotage()
            return
        }
        val random = Random
        var oneIsFalse = false
        for (switch in switches) {
            val data = switch.blockData as Switch
            val value = random.nextBoolean()
            oneIsFalse = oneIsFalse || !value
            data.isPowered = value
            switch.blockData = data
        }
        if (!oneIsFalse) {
            val block = switches.random()
            val switch = block.blockData as Switch
            switch.isPowered = false
            block.blockData = switch
        }

        for (display in lightsBlockDisplays.values) {
            val blockData = display.location.block.blockData
            display.block = blockData
            if (blockData is Switch) {
                display.glowColorOverride = if (blockData.isPowered) Color.GREEN else Color.RED
            }
        }
        val potionEffect = PotionEffect(
            PotionEffectType.BLINDNESS,
            PotionEffect.INFINITE_DURATION,
            1,
            false,
            false,
            false
        )
        for (amongUsPlayer in game.players) {
            if (!amongUsPlayer.canSeeWhenLightsSabotage()) {
                amongUsPlayer.player?.addPotionEffect(potionEffect)
            }
        }
    }

    private fun endLights() {
        for (lever in lightLevers) {
            val block = lever.block
            val switch = block.blockData as? Switch ?: continue
            switch.isPowered = true
            block.blockData = switch
        }
        game.players.forEach { player ->
            player.player?.removePotionEffect(PotionEffectType.DARKNESS)
            lightsBlockDisplays.values.forEach { display ->
                player.player?.hideEntity(AmongUs, display)
            }
        }
    }

    fun lightLeverFlip(lever: Location, player: Player, data: BlockData) {
        if (!lightLevers.contains(lever)) return
        if (checkLightFixed()) {
            endSabotage()
        }
        val block = lever.block
        lightsBlockDisplays[block]?.let { display ->
            if (data is Switch) {
                data.isPowered = !data.isPowered
                display.block = data
                display.glowColorOverride = if (data.isPowered) Color.GREEN else Color.RED
            }
        }
        AmongUs.server.scheduler.runTask(AmongUs) { ->
            if (checkLightFixed()) {
                endSabotage()
            }
        }
        var rightLevers = 0
        var levers = 0
        for (lever in lightLevers) {
            val block = lever.block
            val switch = block.blockData as? Switch ?: continue
            if (switch.isPowered) {
                rightLevers++
            }
            levers++
        }
        bossBar.progress((rightLevers / levers.toFloat()).coerceIn(0.0f, 1.0f))
    }

    private fun checkLightFixed(): Boolean {
        for (lever in lightLevers) {
            val block = lever.block
            if (block.type != Material.LEVER) continue
            val switch = block.blockData as? Switch ?: continue
            if (!switch.isPowered) return false
        }
        return true
    }

    sealed class SabotageType(
        val id: String,
        val activeItem: Material,
        val deactivatedItem: Material = Material.BARRIER,
        val crisis: Boolean,
        internal val startSabotage: SabotageManager.() -> Unit,
        internal val endSabotage: SabotageManager.() -> Unit = {}
    ) {

        object Lights : SabotageType(
            id = "lights",
            activeItem = Material.REDSTONE_TORCH,
            crisis = false,
            startSabotage = SabotageManager::startLights,
            endSabotage = SabotageManager::endLights
        )

        object Communications : SabotageType(
            id = "communications",
            activeItem = Material.COMPASS,
            crisis = false,
            startSabotage = {}
        )

        object ReactorMeltdown : SabotageType(
            id = "reactor_meltdown",
            activeItem = Material.END_CRYSTAL,
            crisis = true,
            startSabotage = {}
        )

        object OxygenDepleted : SabotageType(
            id = "oxygen_depleted",
            activeItem = Material.END_CRYSTAL,
            crisis = true,
            startSabotage = {}
        )

        object ResetSeismic : SabotageType(
            id = "reset_seismic",
            activeItem = Material.END_CRYSTAL,
            crisis = true,
            startSabotage = {}
        )

        data class Door(val uuid: Uuid) : SabotageType(
            id = "door_$uuid",
            activeItem = Material.IRON_DOOR,
            crisis = false,
            startSabotage = {}
        )

        val isCrisis: Boolean get() = crisis
        val isNonCrisis: Boolean get() = !crisis
    }
}