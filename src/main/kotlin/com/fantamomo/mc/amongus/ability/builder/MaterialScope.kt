package com.fantamomo.mc.amongus.ability.builder

import org.bukkit.Material

class MaterialScope(private val builder: AbilityItemBuilder) {
    var active: Material
        get() = builder.activeMaterial
        set(value) { builder.activeMaterial = value }

    var inactive: Material
        get() = builder.inactiveMaterial
        set(value) { builder.inactiveMaterial = value }
}