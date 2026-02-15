package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.Material

object KillAbility :
    Ability<KillAbility, KillAbility.AssignedKillAbility> {

    override val id = "kill"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedKillAbility(player)

    class AssignedKillAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<KillAbility, AssignedKillAbility> {

        override val definition = KillAbility

        override val items = listOf(
            abilityItem("kill") {

                // ---------- TIMER SETUP ----------

                val killCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.KILL.KILL_COOLDOWN]
                )

                // ---------- BLOCK CONDITIONS ----------

                condition {
                    if (!player.isAlive) BlockReason.Dead
                    else null
                }

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    if (player.isVented())
                        BlockReason.InVent
                    else null
                }

                condition {
                    if (!game.killManager.canKillAsImposter(player))
                        BlockReason.custom("notNearVictim")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        material = Material.NETHER_STAR
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
                        material = Material.BARRIER
                        translationKey = when (ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                "ability.kill.kill.dead"
                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom ->
                                "ability.kill.kill.deactivate"

                            else ->
                                "ability.kill.kill.deactivate"
                        }
                    }
                }

                // ---------- COOLDOWN ----------

                state(AbilityItemState.COOLDOWN) {

                    render {
                        material = Material.BARRIER
                        translationKey = "ability.general.disabled.cooldown"
                    }
                }
            }
        )
    }
}
