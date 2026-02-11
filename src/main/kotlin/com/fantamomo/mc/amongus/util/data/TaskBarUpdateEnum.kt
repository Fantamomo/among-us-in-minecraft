package com.fantamomo.mc.amongus.util.data

import com.fantamomo.mc.amongus.util.Colored
import com.fantamomo.mc.amongus.util.MaterialProvider
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material

enum class TaskBarUpdateEnum(private val color: TextColor, private val material: Material) : Colored, MaterialProvider {
    IMMEDIATELY(NamedTextColor.GREEN, Material.GREEN_WOOL),
    MEETING(NamedTextColor.YELLOW, Material.YELLOW_WOOL),
    NONE(NamedTextColor.RED, Material.RED_WOOL);

    override fun color() = color
    override fun material() = material
}