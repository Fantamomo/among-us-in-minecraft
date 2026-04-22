package com.fantamomo.mc.amongus.player.bot.goals

import com.fantamomo.mc.amongus.ability.abilities.EatBodyAbility
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import com.fantamomo.mc.amongus.role.neutral.CannibalRole
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.goal.target.TargetGoal
import org.bukkit.craftbukkit.entity.CraftMannequin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ReportBodyGoal(val zombie: AmongUsZombie) : TargetGoal(zombie, true), CustomGoalDebugName {
    val player = zombie.controller.player

    var targetBody: AmongUsPlayer? = null

    private var ticks: Int = 0

    override fun canUse(): Boolean {
        if (player.role.definition === CannibalRole) return false
        if (player.hasAbility(EatBodyAbility)) return false
        val lastKillTime = player.lastKillTime
        if (lastKillTime != null && lastKillTime + 30.seconds > Clock.System.now()) return false
        findTarget()
        return targetBody != null
    }

    private fun findTarget() {
        val corpse = player.game.killManager.corpses
        for (corpse in corpse) {
            if (zombie.hasLineOfSight((corpse.mannequin as CraftMannequin).handle)) {
                targetBody = corpse.owner
                return
            }
        }
    }

    override fun getFollowDistance() = 50.0

    override fun start() {
        zombie.controller.executionAbilityGoal = this
        zombie.navigation.moveToWithGraph(targetBody!!.location.let { BlockPos(it.blockX, it.blockY, it.blockZ) }, 1.5)
    }

    override fun tick() {
        val target = targetBody ?: return
        val moveToTarget = target.mannequinController.handle!!.blockPosition()
        val distance = 2.0
        if (!moveToTarget.closerToCenterThan(this.mob.position(), distance)) {
            ticks++
            if (ticks % 10 == 0) {
                zombie.navigation.moveToWithGraph(
                    target.mannequinController.handle!!.blockPosition(),
                    1.5
                )
            }
        } else {
            player.game.meetingManager.callMeeting(
                player,
                MeetingManager.MeetingReason.BODY
            )
            targetBody = null
        }
    }

    override fun stop() {
        zombie.controller.executionAbilityGoal = null
        zombie.navigation.stop()
        targetBody = null
        ticks = 0
    }

    override fun getDebugName() = this::class.java.simpleName + targetBody?.name?.let { ": $it" }.orEmpty()
}