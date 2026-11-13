package com.fantamomo.mc.amongus.advancement

interface ProgressiveAdvancement<A : ProgressiveAdvancement<A, AA>, AA : ProgressiveAdvancement.AssignedProgressiveAdvancement<A, AA>> :
    Advancement<A, AA> {

    interface AssignedProgressiveAdvancement<A : ProgressiveAdvancement<A, AA>, AA : AssignedProgressiveAdvancement<A, AA>> :
        AssignedAdvancement<A, AA> {

    }
}