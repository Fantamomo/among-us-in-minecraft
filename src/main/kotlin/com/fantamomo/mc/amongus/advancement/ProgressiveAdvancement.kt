package com.fantamomo.mc.amongus.advancement

interface ProgressiveAdvancement<A : ProgressiveAdvancement<A, AA>, AA : ProgressiveAdvancement.AssignedProgressiveAdvancement<A, AA>> :
    Advancement<A, AA> {

    val max: Int
        get() = 1

    interface AssignedProgressiveAdvancement<A : ProgressiveAdvancement<A, AA>, AA : AssignedProgressiveAdvancement<A, AA>> :
        AssignedAdvancement<A, AA> {
        val current: Int
            get() = 0
    }
}