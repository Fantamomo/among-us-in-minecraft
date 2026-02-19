package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.block.Block
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

object CreateVentAbility : Ability<CreateVentAbility, CreateVentAbility.AssignedCreateVentAbility> {
    override val id: String = "create_vent"

    override fun assignTo(player: AmongUsPlayer) = AssignedCreateVentAbility(player)

    class AssignedCreateVentAbility(override val player: AmongUsPlayer) : AssignedAbility<CreateVentAbility, AssignedCreateVentAbility> {
        override val definition = CreateVentAbility
        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("create_vent") {
                val createVentCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.MINER_CREATE_VENT_COOLDOWN]
                )

                val blockReasonNotOnGround = BlockReason.custom("noBlockBeneath")

                condition {
                    if (!player.isAlive)
                        BlockReason.Dead
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
                    if (player.isNearVent())
                        BlockReason.custom("nearVent")
                    else null
                }

                condition {
                    val p = player.player ?: return@condition null
                    @Suppress("DEPRECATION")
                    if (!p.isOnGround) return@condition blockReasonNotOnGround
                    val blockBeneath: Block = p.location.subtract(0.0, 0.1, 0.0).block
                    if (blockBeneath.type.isSolid()) null
                    else blockReasonNotOnGround
                }

                state(AbilityItemState.ACTIVE) {
                    render {
                        itemType = ItemType.IRON_SHOVEL
                        translationKey = "ability.create_vent.create_vent.active"
                    }

                    onRightClick {
                        restartCooldown = false

                        createVentCooldown.pause()
                        game.ventManager.startCreatingVent(player) { success ->
                            val duration = player.game.settings[SettingsKey.ROLES.MINER_CREATE_VENT_COOLDOWN]
                            if (success) {
                                player.statistics.minerCreatedVents.increment()
                                createVentCooldown.start(duration)
                            } else {
                                createVentCooldown.start(5.seconds.takeIf { duration > it } ?: duration)
                            }
                        }
                    }
                }

                state(AbilityItemState.BLOCKED) {
                    render {
                        itemType = ItemType.BARRIER
                        translationKey = when (val reason = ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                "ability.create_vent.create_vent.deactivate.dead"
                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom if (reason.id == "nearVent") ->
                                "ability.create_vent.create_vent.deactivate.near_vent"

                            is BlockReason.Custom if (reason.id == "noBlockBeneath") ->
                                "ability.create_vent.create_vent.deactivate.no_block_beneath"

                            else ->
                                "ability.create_vent.create_vent.deactivate"
                        }
                    }
                }

                state(AbilityItemState.COOLDOWN) {
                    render {
                        itemType = ItemType.BARRIER
                        translationKey = "ability.general.disabled.cooldown"
                    }
                }
            }
        )
    }
}