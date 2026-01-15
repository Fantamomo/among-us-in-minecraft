package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.seconds

class SabotageManager(private val game: Game) {

    private val cooldownBetweenSabotages =
        game.settings[SettingsKey.SABOTAGE_CRISIS_COOLDOWN].seconds

    private val cooldowns = mutableMapOf<AssignedSabotageType<*, *>, Cooldown>()
    private var currentSabotage: AssignedSabotageType<*, *>? = null

    val bossBar = BossBar.bossBar(
        Component.empty(),
        0f,
        BossBar.Color.RED,
        BossBar.Overlay.PROGRESS
    )

    val supportedSabotages: Map<SabotageType<*, *>, AssignedSabotageType<*, *>> =
        SabotageType.types.mapNotNull { it.create(game) }.associateBy { it.sabotageType }

    fun currentSabotage(): AssignedSabotageType<*, *>? = currentSabotage
    fun currentSabotageType(): SabotageType<*, *>? = currentSabotage?.sabotageType
    fun isCurrentlySabotage() = currentSabotage != null
    fun isCrisis() = currentSabotageType()?.isCrisis == true

    /* ------------------- COOLDOWN ------------------- */

    fun cooldown(type: AssignedSabotageType<*, *>) =
        cooldowns.getOrPut(type) { Cooldown(10.seconds, true) }

    fun canSabotage(type: AssignedSabotageType<*, *>): Boolean =
        type in supportedSabotages.values &&
                currentSabotage == null &&
                cooldown(type).isFinished()

    /* ------------------- FLOW ------------------- */

    fun sabotage(sabotage: AssignedSabotageType<*, *>, ignoreCooldown: Boolean = false): Boolean {
        if (!ignoreCooldown && !canSabotage(sabotage)) return false

        currentSabotage = sabotage

        game.sendTitle(TitlePart.TITLE, textComponent {
            translatable("sabotage.start.${sabotage.sabotageType.id}")
        })

        sabotage.start()


        if (!ignoreCooldown) {
            cooldown(sabotage).apply {
                set(cooldownBetweenSabotages)
                reset(start = true)
            }
        }

        updateBossbarViewerAndWaypoints()
        return true
    }

    fun tick() {
        val sabotage = currentSabotage ?: return
        sabotage.tick()
        updateBossbar()
    }

    fun endSabotage() {
        val sabotage = currentSabotage ?: return

        game.sendTitle(TitlePart.TITLE, textComponent {
            translatable("sabotage.end.${sabotage.sabotageType.id}")
        })

        sabotage.stop(SabotageStopCause.UNKNOWN)

        currentSabotage = null

        updateBossbarViewerAndWaypoints()
    }

    fun updateBossbar() {
        val sabotage = currentSabotage ?: return
        bossBar.progress(sabotage.progress())
        val name = sabotage.bossbarComponent()
        if (name != null) bossBar.name(name)
    }

    /* ------------------- BOSSBAR ------------------- */

    private fun updateBossbarViewerAndWaypoints() {
        val sabotage = currentSabotage
        if (sabotage == null) {
            for (it in bossBar.viewers()) {
                bossBar.removeViewer(it as? Player ?: continue)
            }
            for (player in game.players) {
                for (sabotage in supportedSabotages.values) {
                    for (waypoint in sabotage.waypoints) {
                        game.waypointManager.removeWaypoint(player, waypoint)
                    }
                }
            }
            return
        }

        bossBar.name(
            sabotage.bossbarComponent() ?: Component.translatable("sabotage.bossbar.${sabotage.sabotageType.id}")
        )
        game.players.mapNotNull { it.player }.forEach(bossBar::addViewer)

        for (player in game.players) {
            for (waypoint in sabotage.waypoints) {
                game.waypointManager.assignWaypoint(player, waypoint)
            }
        }
    }
}