package com.fantamomo.mc.amongus.util.data

import com.fantamomo.mc.amongus.util.Colored
import com.fantamomo.mc.amongus.util.MaterialProvider
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material

enum class DistanceEnum(val distance: Double, private val color: TextColor, private val material: Material) : Colored, MaterialProvider {
    SHORT(1.0, NamedTextColor.GREEN, Material.GREEN_WOOL),
    NORMAL(2.0, NamedTextColor.YELLOW, Material.YELLOW_WOOL),
    LONG(4.0, NamedTextColor.RED, Material.RED_WOOL);

    override fun color() = color
    override fun material() = material
}