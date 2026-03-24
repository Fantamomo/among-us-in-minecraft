package com.fantamomo.mc.amongus.task

interface MultiStepTask {
    /**
     * Implementations should make this variable mutable.
     * Starts by 0
     */
    val step: Int

    /**
     * The maximum number of steps that the task can take.
     * Starts by 1
     */
    val maxSteps: Int

    /**
     * Implementations should update the task data (e.g., [AssignedTask.location]) according to the new step.
     *
     * The implementations should increase the [step] variable by 1, it should not check if the increment is correct.
     *
     * This method will not be called if the [step] is already at [maxSteps].
     * It may not throw an exception if the [step] is less then [maxSteps].
     */
    fun nextStep()
}