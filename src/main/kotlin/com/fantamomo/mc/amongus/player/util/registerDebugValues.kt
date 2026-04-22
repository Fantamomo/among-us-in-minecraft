package com.fantamomo.mc.amongus.player.util

import com.destroystokyo.paper.entity.ai.PaperCustomGoal
import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.player.bot.goals.CustomGoalDebugName
import net.minecraft.util.debug.DebugGoalInfo
import net.minecraft.util.debug.DebugGoalInfo.DebugGoal
import net.minecraft.util.debug.DebugPathInfo
import net.minecraft.util.debug.DebugSubscriptions
import net.minecraft.util.debug.DebugValueSource
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.WrappedGoal

fun registerDebugValues(entity: PathfinderMob, registrar: DebugValueSource.Registration) {
    if (AmongUsDebug.DebugValues.BOT_SHOW_PATH.isEnabled()) {
        registrar.register(DebugSubscriptions.ENTITY_PATHS) {
            val path = entity.getNavigation().getPath()
            if (path != null && path.debugData() != null) DebugPathInfo(
                path.copy(),
                entity.getNavigation().getMaxDistanceToWaypoint()
            ) else null
        }
    }
    if (AmongUsDebug.DebugValues.BOT_SHOW_GOALS.isEnabled()) {
        registrar.register(
            DebugSubscriptions.GOAL_SELECTORS
        ) {
            val availableGoals: Set<WrappedGoal> = entity.goalSelector.availableGoals
            val list: MutableList<DebugGoal> = mutableListOf()
            availableGoals.forEach { wrappedGoal: WrappedGoal ->
                val goal = wrappedGoal.goal
                list.add(
                    DebugGoal(
                        wrappedGoal.priority,
                        wrappedGoal.isRunning,
                        when (goal) {
                            is PaperCustomGoal<*> -> goal.getHandle().javaClass.getSimpleName() + "*"
                            is CustomGoalDebugName -> goal.getDebugName()
                            else -> wrappedGoal.goal.javaClass.getSimpleName()
                        }
                    )
                )
            }
            DebugGoalInfo(list)
        }
    }
}