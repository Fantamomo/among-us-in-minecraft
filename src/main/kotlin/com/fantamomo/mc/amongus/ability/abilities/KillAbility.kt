package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.goals.KillNearestPlayerGoal
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType

object KillAbility :
    Ability<KillAbility, KillAbility.AssignedKillAbility> {

    override val id = "kill"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedKillAbility(player)

    class AssignedKillAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<KillAbility, AssignedKillAbility> {

        override val definition = KillAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("kill") {

                // ---------- TIMER SETUP ----------

                val killCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.KILL.KILL_COOLDOWN]
                )

                // ---------- BLOCK CONDITIONS ----------

                requiresAlive()

                requiresNotInMeeting()

                requiresNotInVent()

                registerGoals { gs, zombie, item ->
                    gs.addGoal(5, KillNearestPlayerGoal(zombie, this@AssignedKillAbility, item))
                }

                condition(
                    BlockReason.custom("notNearVictim"),
                    Component.translatable("ability.kill.kill.tooltip")
                ) {
                    !game.killManager.canKillAsImposter(player)
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.NETHER_STAR
                        translationKey = "ability.kill.kill.active"
                    }

                    onRightClick {
                        game.killManager.killNearestAsImposter(player)

                        killCooldown.start()
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        when (ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                translationKey = "ability.kill.kill.dead"


                            is BlockReason.Custom ->
                                translationKey = "ability.kill.kill.deactivate"
                            else -> {}
                        }
                    }
                }
            }
        )
    }
}
