package com.fantamomo.mc.amongus.advancement.utils.step

import com.fantamomo.mc.amongus.advancement.SequenceCancellationException

/**
 * Thrown by `stepFailed()`, the step with the key [stepId] failed.
 */
class StepFailedException(val stepId: String) : SequenceCancellationException("Step '$stepId' failed")
