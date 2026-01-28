package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.Sabotage

object SabotageAbility :
    Ability<SabotageAbility, SabotageAbility.AssignedSabotageAbility> {

    override val id: String = "sabotage"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedSabotageAbility(player)

    class AssignedSabotageAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<SabotageAbility, AssignedSabotageAbility> {

        override val definition = SabotageAbility

        override val items: List<AbilityItem> =
            player.game.sabotageManager.supportedSabotages.values.map { sabotage ->
                sabotageItem(sabotage)
            }

        private fun sabotageItem(
            sabotage: Sabotage<*, *>
        ): AbilityItem = abilityItem(sabotage.sabotageType.id) {

            cooldown {
                game.sabotageManager.cooldown(sabotage)
            }

            material {
                active = sabotage.sabotageType.activeMaterial
                inactive = sabotage.sabotageType.deactivateMaterial
            }

            name {
                active("ability.sabotage.${sabotage.sabotageType.id}.active")
                inactive {
                    whenBlocked(
                        BlockReason.InMeeting,
                        "ability.general.disabled.in_meeting"
                    )
                    whenBlocked(
                        BlockReason.InVent,
                        "ability.general.disabled.in_vent"
                    )
                    whenBlocked(
                        BlockReason.Sabotage,
                        "ability.sabotage.disabled"
                    )
                }
            }

            blockWhen {
                sabotage()
                inMeeting()
                inVent()
            }

            onRightClick {
                game.sabotageManager.sabotage(sabotage)
            }
        }
    }
}