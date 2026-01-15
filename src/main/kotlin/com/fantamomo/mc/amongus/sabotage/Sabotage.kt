package com.fantamomo.mc.amongus.sabotage

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.WaypointManager
import net.kyori.adventure.text.Component

interface Sabotage<S : SabotageType<S, A>, A : Sabotage<S, A>> {
    val game: Game
    val sabotageType: S
    val waypoints: Set<WaypointManager.Waypoint>
        get() = emptySet()

    fun start()

    fun tick() {}

    fun stop(cause: SabotageStopCause)

    fun progress(): Float = -1.0f

    fun bossbarName(): Component? = null

    fun pause() {}

    fun resume() {}
}