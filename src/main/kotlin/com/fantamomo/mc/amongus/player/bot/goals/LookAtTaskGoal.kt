package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import net.minecraft.world.entity.ai.goal.Goal
import org.bukkit.craftbukkit.entity.CraftEntity
import java.util.*

class LookAtTaskGoal(
    private val mob: AmongUsZombie,
) : Goal() {

    init {
        setFlags(EnumSet.of(Flag.LOOK))
    }

    private val moveToTaskGoal get() = mob.controller.moveToTaskGoal

    override fun canUse() = moveToTaskGoal?.state == MoveToTaskGoal.State.WORKING

    override fun tick() {
        val targetTask = moveToTaskGoal?.targetTask ?: return
        mob.lookAt(
            (targetTask.display as CraftEntity).handle,
            30f, 30f
        )
    }
}