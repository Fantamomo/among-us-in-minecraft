package com.fantamomo.mc.amongus.advancement

/**
 * An advancement with a progress from 0 to [max].
 *
 * For a ready to use implementation see [CounterAdvancement].
 */
interface ProgressiveAdvancement<A : ProgressiveAdvancement<A, AA>, AA : ProgressiveAdvancement.AssignedProgressiveAdvancement<A, AA>> :
    Advancement<A, AA> {

    val max: Int

    interface AssignedProgressiveAdvancement<A : ProgressiveAdvancement<A, AA>, AA : AssignedProgressiveAdvancement<A, AA>> :
        AssignedAdvancement<A, AA> {
        val current: Int
    }
}