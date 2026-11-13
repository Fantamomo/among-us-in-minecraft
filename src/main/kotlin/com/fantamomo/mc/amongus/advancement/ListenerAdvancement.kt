package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer

/**
 * Interface to tell the advancement system that this advancement is a listener advancement.
 *
 * That means the advancement is not directly used in code that completes or increments the advancement,
 * instead it listens to events and increments the advancement automatically.
 *
 * Normally the [AssignedAdvancement] instance is only created when it is first needed
 * (via [Advancement.createOf] and [Advancement.new]),
 * but here we will create it immediately, if [shouldCreateAdvancementStateOnGameStart] is true (default).
 *
 * After the creation of the [AssignedAdvancement] we call [AssignedListenerAdvancement.start] and after the game [AssignedListenerAdvancement.stop]
 */
interface ListenerAdvancement {

    /**
     * Whether the advancement should be created immediately on game start.
     *
     * This methode should return false if the advancement is not applicable to the [player]
     * (e.g. it is about killing, and the player can not kill)
     */
    fun shouldCreateAdvancementStateOnGameStart(player: HumanAmongUsPlayer): Boolean = true

    interface AssignedListenerAdvancement {
        fun start(player: HumanAmongUsPlayer)

        fun stop()
    }
}
