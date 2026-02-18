package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.Material

object SheriffKillAbility : Ability<SheriffKillAbility, SheriffKillAbility.AssignedSheriffKillAbility> {
    override val id: String = "sheriff_kill"

    override fun assignTo(player: AmongUsPlayer) = AssignedSheriffKillAbility(player)

    class AssignedSheriffKillAbility(override val player: AmongUsPlayer) : AssignedAbility<SheriffKillAbility, AssignedSheriffKillAbility> {
        override val definition = SheriffKillAbility
        override val items: List<AbilityItem> = listOf(
            abilityItem("sheriff_kill") {
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
                    if (!game.killManager.canKillAsSheriff(player))
                        BlockReason.custom("notNearVictim")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        material = Material.NETHER_STAR
                        translationKey = "ability.sheriff_kill.sheriff_kill.active"
                    }

                    onRightClick {
                        game.killManager.killNearestAsSheriff(player)

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

                            else ->
                                "ability.sheriff_kill.sheriff_kill.deactivate"
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