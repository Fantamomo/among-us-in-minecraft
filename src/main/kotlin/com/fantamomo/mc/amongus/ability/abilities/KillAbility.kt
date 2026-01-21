package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.CooldownAbilityItem
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

object KillAbility : Ability<KillAbility, KillAbility.AssignedKillAbility> {
    override val id: String = "kill"

    override fun assignTo(player: AmongUsPlayer) = AssignedKillAbility(player)

    class AssignedKillAbility(override val player: AmongUsPlayer) : AssignedAbility<KillAbility, AssignedKillAbility> {
        override val definition = KillAbility
        override val items: List<AbilityItem> = listOf(
            abilityItem("kill") {
                material {
                    active = Material.NETHER_STAR
                    inactive = Material.BARRIER
                }

                name {
                    active("ability.kill.kill.active")
                    inactive {
                        whenBlocked(
                            BlockReason.IN_VENT,
                            "ability.general.disabled.in_vent"
                        )
                        whenBlocked(
                            BlockReason.IN_MEETING,
                            "ability.general.disabled.in_meeting"
                        )
                        otherwise("ability.kill.kill.deactivate")
                    }
                }

                blockWhen {
                    inMeeting()
                    inVent()
                    custom(BlockReason.CUSTOM) {
                        !game.killManager.canKillAsImposter(player)
                    }
                }

                onRightClick {
                    game.killManager.killNearestAsImposter(player)
                }
            }
        )
    }

    @Suppress("UnstableApiUsage")
    class KillAbilityItem(ability: AssignedAbility<*, *>) : CooldownAbilityItem(
        ability,
        "kill",
        Cooldown(10.seconds, true)
    ) {
        override fun activatedItem() = ItemStack(Material.NETHER_STAR).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.kill.kill.active")
                }
            )
        }

        override fun cooldownItem(): ItemStack {
            val itemStack = if (canUse()) activatedItem() else deactivatedItem()
            itemStack.setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.general.disabled.cooldown") {
                        args {
                            string("cooldown", cooldown.remaining().toString(DurationUnit.SECONDS, 0))
                            string("ability", ability.definition.id)
                        }
                    }
                }
            )
            return itemStack
        }

        @Suppress("UnstableApiUsage")
        override fun deactivatedItem() = ItemStack(Material.BARRIER).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.kill.kill.deactivate")
                }
            )
        }

        override fun onRightClick() {
            if (!canUse()) {
                ability.player.player?.sendMessage("need to sneak")
                return
            }
            if (cooldown.isFinished()) {
                cooldown.reset(start = true)
                ability.player.player?.sendMessage("kill")
            } else {
                ability.player.player?.sendMessage("not kill")
            }
        }

        override fun canUse() = ability.player.player?.isSneaking == true

        override fun shouldCoundDown() = true
    }
}