package com.fantamomo.mc.amongus.ability.builder

class BlockScope {
    val conditions = mutableListOf<(AbilityContext) -> BlockReason?>()

    fun inMeeting() =
        conditions.add { if (it.game.meetingManager.isCurrentlyAMeeting()) BlockReason.IN_MEETING else null }

    fun sabotage() =
        conditions.add { if (it.game.sabotageManager.isCurrentlySabotage()) BlockReason.SABOTAGE else null }

    fun inVent() =
        conditions.add { if (it.player.isVented()) BlockReason.IN_VENT else null }

    fun dead() =
        conditions.add { if (!it.player.isAlive) BlockReason.DEAD else null }

    fun custom(reason: BlockReason = BlockReason.CUSTOM, check: AbilityContext.() -> Boolean) =
        conditions.add { if (it.check()) reason else null }
}

class RequireScope {
    val conditions = mutableListOf<(AbilityContext) -> BlockReason?>()

    fun sneaking() =
        conditions.add { if (it.player.player?.isSneaking != true) BlockReason.NOT_SNEAKING else null }
}