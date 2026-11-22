package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.imposters.WitchRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType

object PoisonAbility :
    Ability<PoisonAbility, PoisonAbility.AssignedPoisonAbility> {

    override val id = "poison"

    override fun assignTo(player: AmongUsPlayer) = AssignedPoisonAbility(player)

    class AssignedPoisonAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<PoisonAbility, AssignedPoisonAbility> {

        override val definition = PoisonAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("poison") {

                // ---------- TIMER SETUP ----------

                val killCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.WITCH.POISON_COOLDOWN]
                )

                // ---------- BLOCK CONDITIONS ----------

                requiresAlive()

                requiresNotInMeeting()

                requiresNotInVent()

//                registerGoals { gs, zombie, item -> // TODO: fix that
//                    gs.addGoal(5, KillNearestPlayerGoal(zombie, this@AssignedKillAbility, item))
//                }

                condition(
                    BlockReason.custom("notNearVictim"),
                    Component.translatable("ability.poison.poison.tooltip.victim")
                ) {
                    !game.killManager.canPoisonAsWitch(player)
                }

                condition(
                    BlockReason.custom("noAlivePoisonedPlayer"),
                    Component.translatable("ability.poison.poison.tooltip.someone_poisoned")
                ) {
                    (player.role as? WitchRole.AssignedWitchRole)?.canPoisonNewPlayer() != true
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.POTION
                        translationKey = "ability.poison.poison.active"
                        useCustomName = true
                    }

                    onRightClick {
                        game.killManager.poisonNearestAsWitch(player)

                        killCooldown.start()
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        when (val reason = ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                translationKey = "ability.poison.poison.dead"


                            is BlockReason.Custom if (reason.id == "notNearVictim") ->
                                translationKey = "ability.poison.poison.deactivate.victim"

                            is BlockReason.Custom if (reason.id == "noAlivePoisonedPlayer") ->
                                translationKey = "ability.poison.poison.deactivate.victim"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}