package com.fantamomo.mc.amongus.modification

import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier

interface AssignedAttributeModifier<M : Modification<M, A>, A : AssignedAttributeModifier<M, A>> :
    AssignedModification<M, A> {

    val attribute: Attribute
    val modifier: AttributeModifier

    override fun onStart() {
        player.player?.getAttribute(attribute)?.addTransientModifier(modifier)
    }

    override fun onEnd() {
        player.player?.getAttribute(attribute)?.removeModifier(modifier)
    }
}