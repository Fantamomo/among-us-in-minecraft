package com.fantamomo.mc.amongus.role.util

enum class WinCheckPhase {
    /**
     * Before any win condition is checked
     */
    PRE,
    /**
     * After the task completion check
     */
    POST_TASK_CHECK,
    /**
     * After all default checks (tasks, imposters)
     */
    POST
}