package com.fantamomo.mc.amongus.modification.modifications

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.modification.AssignedAttributeModifier
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier

object SpeedModification : Modification<SpeedModification, SpeedModification.AssignedSpeedModification> {
    override val id: String = "speed"

    override fun assignTo(player: AmongUsPlayer) = AssignedSpeedModification(player)

    class AssignedSpeedModification(override val player: AmongUsPlayer) : AssignedAttributeModifier<SpeedModification, AssignedSpeedModification> {
        override val definition = SpeedModification

        override val attribute: Attribute = Attribute.MOVEMENT_SPEED
        override val modifier = Companion.modifier

        companion object {
            private val key = NamespacedKey(AmongUs, "modification/$id")
            private val modifier = AttributeModifier(key, 0.05, AttributeModifier.Operation.ADD_NUMBER)
        }
    }
}