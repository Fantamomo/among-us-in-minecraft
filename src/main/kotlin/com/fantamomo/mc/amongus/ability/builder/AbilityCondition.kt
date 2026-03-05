package com.fantamomo.mc.amongus.ability.builder

fun interface AbilityCondition {
    fun AbilityContext.check(): BlockReason?

    fun checkInternal(ctx: AbilityContext) = ctx.check()
}

interface BooleanAbilityCondition : AbilityCondition {
    val reason: BlockReason

    fun blocked(ctx: AbilityContext): Boolean

    override fun AbilityContext.check() = reason.takeIf { blocked(this) }

    class Impl(
        override val reason: BlockReason,
        private val block: AbilityContext.() -> Boolean
    ) : BooleanAbilityCondition {
        override fun blocked(ctx: AbilityContext): Boolean = ctx.block()
    }
}

fun AbilityItemBuilder.requiresNotInVent(): Unit =
    condition(BlockReason.InVent) { player.isVented() }

fun AbilityItemBuilder.requiresNotInMeeting(): Unit =
    condition(BlockReason.InMeeting) { player.game.meetingManager.isCurrentlyAMeeting() }

fun AbilityItemBuilder.requiresNoSabotage(): Unit =
    condition(BlockReason.Sabotage) { player.game.sabotageManager.isCurrentlySabotage() }

fun AbilityItemBuilder.requiresAlive(): Unit =
    condition(BlockReason.Dead) { !player.isAlive }

fun AbilityItemBuilder.requiresNotInGhostForm(): Unit =
    condition(BlockReason.GhostForm) { player.isInGhostForm() }