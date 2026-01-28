package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import org.bukkit.Material
import kotlin.time.Duration.Companion.seconds

object VentAbility :
    Ability<VentAbility, VentAbility.AssignedVentAbility> {

    override val id: String = "vent"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedVentAbility(player)

    class AssignedVentAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<VentAbility, AssignedVentAbility> {

        override val definition = VentAbility

        override val items: List<AbilityItem> = listOf(
            abilityItem("vent") {
                cooldown {
                    Cooldown(game.settings[SettingsKey.VENT_COOLDOWN].seconds)
                }

                material {
                    active = Material.TRIPWIRE_HOOK
                    inactive = Material.BARRIER
                }

                name {
                    active {
                        if (player.isVented())
                            "ability.vent.vent.out"
                        else
                            "ability.vent.vent.in"
                    }
                    inactive {
                        whenBlocked(
                            BlockReason.InVent,
                            "ability.general.disabled.in_vent"
                        )
                        whenBlocked(
                            "notNearVent",
                            "ability.vent.vent.deactivate"
                        )
                    }
                }

                blockWhen {
                    custom("notNearVent") {
                        game.ventManager.run {
                            if (isVented(player)) false
                            else !isNearVent(player)
                        }
                    }
                    inMeeting()
                }

                onRightClick {
                    game.ventManager.doVent(player)
                }

                onLeftClick {
                    if (!player.isVented()) return@onLeftClick
                    game.ventManager.nextVent(player)
                }
            }
        )
    }
}