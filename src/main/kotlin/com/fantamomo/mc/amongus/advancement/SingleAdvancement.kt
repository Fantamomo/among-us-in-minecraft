package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.util.*

/**
 * An advancement that is either completed or not, without any progress in between.
 *
 * There are two ways to complete it:
 *
 * 1. **From code**: call [award] (or `of(player).award()`) wherever the condition is met.
 * 2. **As listener**: override [listen] and register events or statistics there.
 *    The listeners are registered at game start and removed at game end or when the advancement is completed.
 *
 * ```
 * object FirstGameAdvancement : SingleAdvancement("first_game") {
 *     override fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {
 *         onChange(player.statistics.playedGames) { _, _ -> award() }
 *     }
 * }
 * ```
 *
 * @author Fantamomo
 * @since 2.4-SNAPSHOT
 */
abstract class SingleAdvancement(override val id: String) :
    Advancement<SingleAdvancement, SingleAdvancement.AssignedSingleAdvancement>, ListenerAdvancement {

    /**
     * Registers the listeners for [AdvancementListenerScope.player].
     *
     * Only called if the advancement is not yet completed
     * and [shouldCreateAdvancementStateOnGameStart] returns true.
     */
    open fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {}

    fun award(player: HumanAmongUsPlayer) = of(player).award()

    override fun new(player: UUID) = AssignedSingleAdvancement(this, player)

    override fun createOf(player: UUID, data: JsonElement) = AssignedSingleAdvancement(this, player)

    class AssignedSingleAdvancement(
        override val definition: SingleAdvancement,
        override val player: UUID,
    ) : AssignedAdvancement<SingleAdvancement, AssignedSingleAdvancement>,
        ListenerAdvancement.AssignedListenerAdvancement {

        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false

        private var scope: AdvancementListenerScope<AssignedSingleAdvancement>? = null

        override fun start(player: HumanAmongUsPlayer) {
            stop()
            val scope = AdvancementListenerScope(this, player)
            this.scope = scope
            with(definition) { scope.listen() }
        }

        override fun stop() {
            scope?.dispose()
            scope = null
        }

        override fun toJson() = JsonNull
    }
}
