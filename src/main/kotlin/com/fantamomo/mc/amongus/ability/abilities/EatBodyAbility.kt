package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.neutral.CannibalRole
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType

object EatBodyAbility : Ability<EatBodyAbility, EatBodyAbility.AssignedEatBodyAbility> {
    override val id: String = "eat_body"

    override fun canAssignTo(player: AmongUsPlayer) = player.assignedRole?.definition === CannibalRole

    override fun assignTo(player: AmongUsPlayer) = AssignedEatBodyAbility(player)

    class AssignedEatBodyAbility(override val player: AmongUsPlayer) :
        AssignedAbility<EatBodyAbility, AssignedEatBodyAbility> {
        override val definition = EatBodyAbility

        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("eat_body") {

                requiresNotInMeeting()

                requiresAlive()

                condition(
                    BlockReason.custom("notNearCorpse"),
                    Component.translatable("ability.eat_body.eat_body.tooltip")
                ) {
                    val loc = player.livingEntity.location
                    !game.killManager.isNearCorpse(loc)
                }

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.CLAY_BALL
                        translationKey = "ability.eat_body.eat_body.active"
                    }

                    onRightClick {
                        player.game.killManager.eatCorpse(player)
                    }
                }

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        when (val reason = ctx.getBlockReason()) {

                            BlockReason.Dead -> translationKey =
                                "ability.eat_body.eat_body.deactivate.dead"

                            is BlockReason.Custom -> translationKey = when (reason.id) {
                                "notNearCorpse" ->
                                    "ability.eat_body.eat_body.deactivate.not_near_corpse"

                                else ->
                                    "ability.eat_body.eat_body.deactivate"
                            }
                            else -> {}
                        }
                    }
                }
            }
        )
    }
}