package com.fantamomo.mc.amongus.advancement.progressive

import kotlin.uuid.Uuid

/**
 * Detects players who farm [VentRatAdvancement] by repeatedly entering and exiting the same vent.
 *
 * A **bounce** is entering the exact vent the player just exited from,
 * while they did not travel to another vent during the previous visit.
 * Real vent usage (entering one vent and leaving through another) is never a bounce.
 *
 * This intentionally does not look at timing: the vent cooldown is configurable,
 * and a cheater can simply click slower.
 *
 * Kept free of Bukkit types so that it can be unit tested.
 */
internal class VentBounceDetector(
    private val maxStreak: Int = MAX_BOUNCE_STREAK,
    private val maxBounces: Int = MAX_TOTAL_BOUNCES,
) {
    private class Visit(val enteredVent: Uuid) {
        var exitedVent: Uuid? = null
    }

    private val visits = mutableListOf<Visit>()

    fun onEnter(vent: Uuid) {
        visits.add(Visit(vent))
    }

    fun onExit(vent: Uuid) {
        val visit = visits.lastOrNull() ?: return
        if (visit.exitedVent == null) visit.exitedVent = vent
    }

    fun clear() = visits.clear()

    /**
     * True if the player bounced [maxStreak] times in a row,
     * or [maxBounces] times in total (so mixing in a few real vents does not help).
     */
    fun isCheating(): Boolean {
        var bounces = 0
        var streak = 0
        var longestStreak = 0

        for (i in 1 until visits.size) {
            val previous = visits[i - 1]
            val stayedInPlace = previous.exitedVent == previous.enteredVent
            val isBounce = stayedInPlace && visits[i].enteredVent == previous.exitedVent

            if (isBounce) {
                bounces++
                streak++
                longestStreak = maxOf(longestStreak, streak)
            } else {
                streak = 0
            }
        }

        return longestStreak >= maxStreak || bounces >= maxBounces
    }

    companion object {
        /** Peeking out of a vent and going back in a few times is normal play. */
        const val MAX_BOUNCE_STREAK = 5

        /** More than half of the 15 required entries being bounces is farming. */
        const val MAX_TOTAL_BOUNCES = 8
    }
}
