package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.ability.abilities.KillAbility
import com.fantamomo.mc.amongus.ability.builder.DSLAbilityItem
import com.fantamomo.mc.amongus.player.AmongUsMannequin
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import java.util.*
import kotlin.time.Duration

class KillNearestPlayerGoal(
    private val mob: AmongUsZombie,
    private val ability: KillAbility.AssignedKillAbility,
    private val item: DSLAbilityItem,
    private val speedModifier: Double = 1.5,
) : Goal(), CustomGoalDebugName {
    private var target: AmongUsPlayer? = null
    private var ticks = 0
    private val lookAtContext: TargetingConditions

    init {
        this.setFlags(EnumSet.of(Flag.MOVE))
        this.lookAtContext = TargetingConditions.forNonCombat().range(40.0)
    }

    override fun canUse(): Boolean {
        if (!mob.controller.player.isAlive()) return false
        if (item.remainingCooldown() > Duration.ZERO) return false
        val serverLevel = getServerLevel(this.mob)
        val t = serverLevel.getNearestEntity(
            serverLevel.getEntitiesOfClass(
                AmongUsMannequin::class.java,
                this.mob.boundingBox.inflate(40.0),
                ::checkTarget
            ),
            this.lookAtContext,
            this.mob,
            this.mob.x,
            this.mob.eyeY,
            this.mob.z
        )
        this.target = t?.controller?.owner
        return t != null
    }

    override fun canContinueToUse() = target != null && target!!.isAlive() && mob.controller.player.isAlive() && item.remainingCooldown() <= Duration.ZERO

    override fun requiresUpdateEveryTick(): Boolean = true

    private fun checkTarget(target: AmongUsMannequin): Boolean {
        val owner = target.controller.owner
        if (owner.role.definition.team == Team.IMPOSTERS) return false
        return mob.controller.player.canSee(owner)
    }

    override fun start() {
//        mob.controller.handle.navigation.moveToWithGraph(target!!.mannequinController.handle!!.blockPosition(), speedModifier)
    }

    override fun tick() {
        val target = target ?: return
        val moveToTarget = target.mannequinController.handle!!.blockPosition()
        val distance = target.game.settings[SettingsKey.KILL.KILL_DISTANCE].distance
        if (!moveToTarget.closerToCenterThan(this.mob.position(), distance)) {
            ticks++
            if (ticks % 10 == 0) {
                mob.controller.handle.navigation.moveToWithGraph(
                    target.mannequinController.handle!!.blockPosition(),
                    speedModifier
                )
            }
        } else {
            target.game.killManager.killByImposter(mob.controller.player, target)
            this.target = null
        }
    }

    override fun stop() {
        this.mob.getNavigation().stop()
        target = null
        ticks = 0
    }

    override fun getDebugName() = this::class.java.simpleName + target?.name?.let { ": $it" }.orEmpty()
}