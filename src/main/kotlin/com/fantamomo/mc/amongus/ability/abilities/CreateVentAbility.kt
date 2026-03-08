package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.imposters.MinerRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

object CreateVentAbility : Ability<CreateVentAbility, CreateVentAbility.AssignedCreateVentAbility> {
    override val id: String = "create_vent"

    override fun assignTo(player: AmongUsPlayer) = AssignedCreateVentAbility(player)

    class AssignedCreateVentAbility(override val player: AmongUsPlayer) :
        AssignedAbility<CreateVentAbility, AssignedCreateVentAbility> {
        override val definition = CreateVentAbility

        @Suppress("UnstableApiUsage")
        override val items: List<AbilityItem> = listOf(
            abilityItem("create_vent") {
                val createVentCooldown = timer(
                    "cooldown",
                    player.game.settings[SettingsKey.ROLES.MINER.CREATE_VENT_COOLDOWN]
                )

                val blockReasonNotOnGround = BlockReason.custom("noBlockBeneath")

                requiresAlive()

                requiresNotInMeeting()

                requiresNotInVent()

                condition(
                    BlockReason.custom("nearVent"),
                    Component.translatable("ability.create_vent.create_vent.tooltip.near_vent")
                ) {
                    player.isNearVent()
                }

                condition(
                    blockReasonNotOnGround,
                    Component.translatable("ability.create_vent.create_vent.tooltip.not_on_ground")
                ) {
                    val p = player.player ?: return@condition false
                    @Suppress("DEPRECATION")
                    if (!p.isOnGround) return@condition true
                    val blockBeneath: Block = p.location.subtract(0.0, 0.1, 0.0).block
                    !blockBeneath.type.isSolid()
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
                            val duration = player.game.settings[SettingsKey.ROLES.MINER.CREATE_VENT_COOLDOWN]
                            if (success) {
                                (player.assignedRole as? MinerRole.AssignedMinerRole)?.createdVents++
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
                        when (val reason = ctx.getBlockReason()) {
                            BlockReason.Dead ->
                                translationKey = "ability.create_vent.create_vent.deactivate.dead"

                            is BlockReason.Custom if (reason.id == "nearVent") ->
                                translationKey = "ability.create_vent.create_vent.deactivate.near_vent"

                            is BlockReason.Custom if (reason.id == "noBlockBeneath") ->
                                translationKey = "ability.create_vent.create_vent.deactivate.no_block_beneath"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}