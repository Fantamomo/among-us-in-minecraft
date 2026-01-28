package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility

class BlockScope<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val conditions = mutableListOf<(AbilityContext<A, S>) -> BlockReason?>()

    fun inMeeting() =
        conditions.add { if (it.game.meetingManager.isCurrentlyAMeeting()) BlockReason.IN_MEETING else null }

    fun sabotage() =
        conditions.add { if (it.game.sabotageManager.isCurrentlySabotage()) BlockReason.SABOTAGE else null }

    fun inVent() =
        conditions.add { if (it.player.isVented()) BlockReason.IN_VENT else null }

    fun dead() =
        conditions.add { if (!it.player.isAlive) BlockReason.DEAD else null }

    fun custom(reason: BlockReason = BlockReason.CUSTOM, check: AbilityContext<A, S>.() -> Boolean) =
        conditions.add { if (it.check()) reason else null }
}

class RequireScope<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val conditions = mutableListOf<(AbilityContext<A, S>) -> BlockReason?>()

    fun sneaking() =
        conditions.add { if (it.player.player?.isSneaking != true) BlockReason.NOT_SNEAKING else null }
}