package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import org.bukkit.Material

class MaterialScope<A : Ability<A, S>, S : AssignedAbility<A, S>>(
    private val builder: AbilityItemBuilder<A, S>
) {
    var active: Material
        get() = builder.activeMaterial
        set(value) { builder.activeMaterial = value }

    var inactive: Material
        get() = builder.inactiveMaterial
        set(value) { builder.inactiveMaterial = value }
}