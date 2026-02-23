package com.fantamomo.mc.amongus.modification.modifications

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier

object SmallModification : Modification<SmallModification, SmallModification.AssignedSmallModification> {
    override val id: String = "small"

    override fun assignTo(player: AmongUsPlayer) = AssignedSmallModification(player)

    class AssignedSmallModification(override val player: AmongUsPlayer) : AssignedModification<SmallModification, AssignedSmallModification> {
        override val definition = SmallModification

        override fun onStart() {
            player.player?.getAttribute(Attribute.SCALE)?.addTransientModifier(modifier)
        }

        override fun onEnd() {
            player.player?.getAttribute(Attribute.SCALE)?.removeModifier(modifier)
        }

        companion object {
            private val key = NamespacedKey(AmongUs, "modifier/small")
            private val modifier = AttributeModifier(key, -0.5, AttributeModifier.Operation.ADD_NUMBER)
        }
    }
}