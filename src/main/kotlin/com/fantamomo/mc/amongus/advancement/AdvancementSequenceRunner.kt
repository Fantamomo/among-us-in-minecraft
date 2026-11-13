package com.fantamomo.mc.amongus.advancement

class AdvancementSequenceRunner(val runner: suspend AdvancementSequence.() -> Unit)

fun SequencedAdvancement.runner(runner: suspend AdvancementSequence.() -> Unit) = AdvancementSequenceRunner(runner)