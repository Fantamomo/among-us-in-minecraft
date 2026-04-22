package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.player.AmongUsMannequin
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import net.minecraft.world.entity.decoration.Mannequin
import java.util.*

class LookAtPlayerGoal(
    private val mob: AmongUsZombie,
    private val lookDistance: Float,
    private val probability: Float = DEFAULT_PROBABILITY
) : Goal() {
    private var lookAt: Entity? = null
    private var lookTime = 0
    private val lookAtContext: TargetingConditions

    private val player: BotAmongUsPlayer
        get() = mob.controller.player

    init {
        this.setFlags(EnumSet.of(Flag.LOOK))
        this.lookAtContext = TargetingConditions.forNonCombat().range(lookDistance.toDouble())
    }

    override fun canUse(): Boolean {
        if (this.mob.getRandom().nextFloat() >= this.probability) {
            return false
        } else {
            if (this.mob.target != null) {
                this.lookAt = this.mob.target
            }

            val serverLevel = getServerLevel(this.mob)
            this.lookAt = serverLevel.getNearestEntity(
                serverLevel.getEntitiesOfClass(
                    Mannequin::class.java,
                    this.mob.boundingBox.inflate(this.lookDistance.toDouble(), 3.0, this.lookDistance.toDouble()),
                    ::checkTarget
                ),
                this.lookAtContext,
                this.mob,
                this.mob.x,
                this.mob.eyeY,
                this.mob.z
            )

            return this.lookAt != null
        }
    }

    private fun checkTarget(target: Mannequin): Boolean {
        if (target !is AmongUsMannequin) return false
        val owner = target.controller.owner
        return player.canSee(owner)
    }

    override fun canContinueToUse(): Boolean {
        return lookAt!!.isAlive && !(mob.distanceToSqr(lookAt!!) > (lookDistance * lookDistance).toDouble()) && lookTime > 0
    }

    override fun start() {
        lookTime = adjustedTickDelay(40 + this.mob.getRandom().nextInt(40))
    }

    override fun stop() {
        lookAt = null
    }

    override fun tick() {
        if (lookAt!!.isAlive) {
            val d = this.lookAt!!.eyeY
            mob.getLookControl().setLookAt(this.lookAt!!.x, d, this.lookAt!!.z)
            lookTime--
        }
    }

    companion object {
        const val DEFAULT_PROBABILITY: Float = 0.02f
    }
}
