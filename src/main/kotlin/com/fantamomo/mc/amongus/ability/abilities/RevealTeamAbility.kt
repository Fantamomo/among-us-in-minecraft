package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.inventory.ItemType
import kotlin.time.Duration

object RevealTeamAbility : Ability<RevealTeamAbility, RevealTeamAbility.AssignedRevealTeamAbility> {
    override val id: String = "reveal_team"

    override fun assignTo(player: AmongUsPlayer) = AssignedRevealTeamAbility(player)

    class AssignedRevealTeamAbility(override val player: AmongUsPlayer) : AssignedAbility<RevealTeamAbility, AssignedRevealTeamAbility> {
        override val definition = RevealTeamAbility

        private val revealedPlayers = mutableListOf<AmongUsPlayer>()

        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("reveal_team") {
                val revealTeamCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.REVEAL_TEAM.START_COOLDOWN]
                )

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    val thisLoc = (player.mannequinController.getEntity() ?: player.livingEntity).location
                    val maxDistance = player.game.settings[SettingsKey.ROLES.REVEAL_TEAM.DISTANCE].distance.let { it * it }
                    for (player in game.players) {
                        if (player === this@AssignedRevealTeamAbility.player) continue
                        if (!player.isAlive && this@AssignedRevealTeamAbility.player.isAlive) continue
                        if (player.isVented()) continue
                        if (player in revealedPlayers) continue
                        val loc = (player.mannequinController.getEntity() ?: player.livingEntity).location
                        if (thisLoc.distanceSquared(loc) < maxDistance) return@condition null
                    }

                    BlockReason.custom("not_near_player")
                }

                state(AbilityItemState.ACTIVE) {
                    render {
                        itemType = ItemType.ENDER_EYE
                        translationKey = "ability.reveal_team.reveal_team.active"
                    }

                    onRightClick {
                        restartCooldown = false
                        val thisLoc = (player.mannequinController.getEntity() ?: player.livingEntity).location
                        val maxDistance = player.game.settings[SettingsKey.ROLES.REVEAL_TEAM.DISTANCE].distance.let { it * it }
                        var nearestPlayer: AmongUsPlayer? = null
                        var nearestDistance = Double.MAX_VALUE
                        for (player in game.players) {
                            if (player === this@AssignedRevealTeamAbility.player) continue
                            if (!player.isAlive && this@AssignedRevealTeamAbility.player.isAlive) continue
                            if (player.isVented()) continue
                            if (player in revealedPlayers) continue
                            val loc = (player.mannequinController.getEntity() ?: player.livingEntity).location
                            val distance = thisLoc.distanceSquared(loc)
                            if (distance < maxDistance && distance < nearestDistance) {
                                nearestPlayer = player
                                nearestDistance = distance
                            }
                        }
                        if (nearestPlayer == null) return@onRightClick
                        revealedPlayers.add(nearestPlayer)
                        val team = (nearestPlayer.assignedRole?.definition?.team ?: Team.CREWMATES)
                        val color = team.textColor
                        player.statistics.seerRevealedTeams[team]?.increment()
                        val p = player.player
                        if (p != null) {
                            nearestPlayer.mannequinController.setNameColorFor(p, color)
                        } else {
                            nearestPlayer.mannequinController.setNameColorFor(player.uuid, color)
                        }
                        val timeToAdd = player.game.settings[SettingsKey.ROLES.REVEAL_TEAM.COOLDOWN_INCREMENT]
                        if (timeToAdd <= Duration.ZERO) {
                            revealTeamCooldown.start()
                        } else {
                            val old = revealTeamCooldown.handle.startDuration()
                            val new = old + timeToAdd
                            revealTeamCooldown.start(new)
                        }
                    }
                }

                state(AbilityItemState.BLOCKED) {
                    render {
                        itemType = ItemType.BARRIER
                        when (ctx.getBlockReason()) {
                            is BlockReason.Custom ->
                                translationKey = "ability.reveal_team.reveal_team.deactivate"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}