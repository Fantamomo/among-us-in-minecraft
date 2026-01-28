package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility

class BlockScope<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val conditions = mutableListOf<(AbilityContext<A, S>) -> BlockReason?>()

    fun inMeeting() =
        conditions.add { if (it.game.meetingManager.isCurrentlyAMeeting()) BlockReason.InMeeting else null }

    fun sabotage() =
        conditions.add { if (it.game.sabotageManager.isCurrentlySabotage()) BlockReason.Sabotage else null }

    fun inVent() =
        conditions.add { if (it.player.isVented()) BlockReason.InVent else null }

    fun dead() =
        conditions.add { if (!it.player.isAlive) BlockReason.Dead else null }

    fun custom(id: String, check: AbilityContext<A, S>.() -> Boolean) =
        custom(BlockReason.Custom(id), check)

    fun custom(reason: BlockReason, check: AbilityContext<A, S>.() -> Boolean) =
        conditions.add { if (it.check()) reason else null }
}

class RequireScope<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val conditions = mutableListOf<(AbilityContext<A, S>) -> BlockReason?>()

}