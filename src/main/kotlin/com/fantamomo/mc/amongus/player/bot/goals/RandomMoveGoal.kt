package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.ai.util.DefaultRandomPos

class RandomMoveGoal(mob: AmongUsZombie, speedModifier: Double, interval: Int, val range: Int) :
    RandomStrollGoal(mob, speedModifier, interval, false) {

    private val auZombie: AmongUsZombie
        get() = super.mob as AmongUsZombie

    override fun getPosition() = DefaultRandomPos.getPos(auZombie, range, 7)

    override fun canContinueToUse(): Boolean {
        return auZombie.controller.moveToTaskGoal?.state.let { it == null || it == MoveToTaskGoal.State.COOLDOWN } && super.canContinueToUse()
    }
}