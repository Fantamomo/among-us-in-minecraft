package com.fantamomo.mc.amongus.advancement.utils.step

class StepFailedException(val stepId: String) : Exception("Marker exception in the sequenced step system to tell that the step failed")