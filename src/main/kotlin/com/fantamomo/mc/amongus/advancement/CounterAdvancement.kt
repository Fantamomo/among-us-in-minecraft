package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.progressive.VentRatAdvancement
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.statistics.CounterStatistic
import kotlinx.serialization.json.*
import java.util.*

/**
 * A [ProgressiveAdvancement] that counts from 0 up to [max] and is awarded once [max] is reached.
 *
 * There are two ways to make progress:
 *
 * 1. **From code**: call [increment] (or `of(player).increment()` / `of(player).progressTo(value)`).
 * 2. **As listener**: override [listen] and use [increment] or [mirror] inside it.
 *
 * ```
 * object VeteranAdvancement : CounterAdvancement("veteran", 100) {
 *     override fun AdvancementListenerScope<AssignedCounterAdvancement>.listen() {
 *         mirror(player.statistics.playedGames)
 *     }
 * }
 * ```
 *
 * @author Fantamomo
 * @since 2.4-SNAPSHOT
 */
abstract class CounterAdvancement(override val id: String, final override val max: Int) :
    ProgressiveAdvancement<CounterAdvancement, CounterAdvancement.AssignedCounterAdvancement>, ListenerAdvancement {

    init {
        require(max > 0) { "max must be positive (was $max)" }
    }

    /**
     * If true, the progress only counts within a single game:
     * it is reset at the start and end of every game (unless completed) and not saved.
     *
     * Use this for objectives like "Do X 5 times in one game".
     */
    open val resetEachGame: Boolean
        get() = false

    /**
     * Registers the listeners for [AdvancementListenerScope.player].
     *
     * Only called if the advancement is not yet completed
     * and [shouldCreateAdvancementStateOnGameStart] returns true.
     */
    open fun AdvancementListenerScope<AssignedCounterAdvancement>.listen() {}

    fun increment(player: HumanAmongUsPlayer, amount: Int = 1) = of(player).increment(amount)

    override fun new(player: UUID) = AssignedCounterAdvancement(this, player)

    override fun createOf(player: UUID, data: JsonElement): AssignedCounterAdvancement {
        val current = if (resetEachGame) 0 else
            ((data as? JsonObject)?.get("current") as? JsonPrimitive)?.intOrNull ?: 0
        return AssignedCounterAdvancement(this, player, current.coerceIn(0, max))
    }

    class AssignedCounterAdvancement(
        override val definition: CounterAdvancement,
        override val player: UUID,
        current: Int = 0,
    ) : ProgressiveAdvancement.AssignedProgressiveAdvancement<CounterAdvancement, AssignedCounterAdvancement>,
        ListenerAdvancement.AssignedListenerAdvancement {

        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false

        override var current: Int = current
            private set

        private var scope: AdvancementListenerScope<AssignedCounterAdvancement>? = null

        fun reset() {
            if (isCompleted) return
            current = 0
        }

        fun increment(amount: Int = 1): Int {
            require(amount >= 0) { "amount must not be negative (was $amount)" }
            if (amount == 0) return current
            val value = current.toLong().plus(amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            progressTo(value)
            return value
        }

        /**
         * Sets the progress to [value], if it is higher than the [current] progress.
         * Progress never goes backwards (except for [resetEachGame]).
         */
        fun progressTo(value: Int) {
            if (isCompleted || value <= current) return
            current = value.coerceAtMost(definition.max)
            if (current >= definition.max) award()
        }

        private fun resetIfPerGame() {
            if (definition.resetEachGame && !isCompleted) current = 0
        }

        override fun start(player: HumanAmongUsPlayer) {
            stop()
            resetIfPerGame()
            val scope = AdvancementListenerScope(this, player)
            this.scope = scope
            with(definition) { scope.listen() }
        }

        override fun stop() {
            scope?.dispose()
            scope = null
            resetIfPerGame()
        }

        override fun toJson(): JsonElement {
            if (definition === VentRatAdvancement) {
                /**
                 * if its the VentRatAdvancement we store the cheater count for the player (if exists)
                 * @see VentRatAdvancement.createOf
                 */
                (val count, val lastCheat) = VentRatAdvancement.cheaters[player] ?: return JsonNull
                return buildJsonObject {
                    putJsonObject("cheater") {
                        put("count", count)
                        put("last_cheat", lastCheat)
                    }
                }
            }
            if (definition.resetEachGame || current == 0) return JsonNull
            return buildJsonObject { put("current", current) }
        }
    }
}


val AdvancementListenerScope<CounterAdvancement.AssignedCounterAdvancement>.current: Int
    get() = assigned.current

fun AdvancementListenerScope<CounterAdvancement.AssignedCounterAdvancement>.increment(amount: Int = 1) =
    assigned.increment(amount)

fun AdvancementListenerScope<CounterAdvancement.AssignedCounterAdvancement>.reset() = assigned.reset()

/**
 * Keeps the progress in sync with [statistic].
 *
 * Because the statistic already contains everything the player did before this advancement existed,
 * players get credit for their past games.
 */
fun AdvancementListenerScope<CounterAdvancement.AssignedCounterAdvancement>.mirror(statistic: CounterStatistic) {
    assigned.progressTo(statistic.value)
    onChange(statistic) { now, _ -> assigned.progressTo(now) }
}

/**
 * Keeps the progress in sync with the sum of all [statistics].
 */
fun AdvancementListenerScope<CounterAdvancement.AssignedCounterAdvancement>.mirror(statistics: Collection<CounterStatistic>) {
    fun sum() = statistics.sumOf { it.value }
    assigned.progressTo(sum())
    for (statistic in statistics) {
        onChange(statistic) { _, _ -> assigned.progressTo(sum()) }
    }
}
