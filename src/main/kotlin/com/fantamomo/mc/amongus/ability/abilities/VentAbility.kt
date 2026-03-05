package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

object VentAbility :
    Ability<VentAbility, VentAbility.AssignedVentAbility> {

    override val id = "vent"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedVentAbility(player)

    class AssignedVentAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<VentAbility, AssignedVentAbility> {

        override val definition = VentAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("vent") {

                // ---------- MODULAR TIMER ----------

                timer("cooldown", player.game.settings[SettingsKey.VENT.VENT_COOLDOWN].seconds)

                // ---------- CONDITIONS ----------

                requiresNotInMeeting()

                condition(
                    BlockReason.custom("notNearVent"),
                    Component.translatable("ability.vent.vent.tooltip")
                ) {
                    val ventManager = game.ventManager

                    !ventManager.isVented(player) &&
                        !ventManager.isNearVent(player)
                }

                requiresNotInGhostForm()

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.TRIPWIRE_HOOK
                        translationKey =
                            if (player.isVented())
                                "ability.vent.vent.out"
                            else
                                "ability.vent.vent.in"
                    }

                    onRightClick {

                        game.ventManager.doVent(player)

                        // Start cooldown when toggling vent
                        getTimer("cooldown")?.start()
                    }

                    onLeftClick {

                        if (!player.isVented()) return@onLeftClick

                        game.ventManager.nextVent(player)
                    }
                }

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        itemType = ItemType.BARRIER
                        when (ctx.getBlockReason()) {
                            is BlockReason.Custom ->
                                translationKey = "ability.vent.vent.deactivate"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}