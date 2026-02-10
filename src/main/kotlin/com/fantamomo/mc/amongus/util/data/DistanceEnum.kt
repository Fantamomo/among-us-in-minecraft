package com.fantamomo.mc.amongus.util.data

import com.fantamomo.mc.amongus.util.Colored
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

enum class DistanceEnum(val distance: Double, private val color: TextColor) : Colored {
    SHORT(1.0, NamedTextColor.GREEN),
    NORMAL(2.0, NamedTextColor.YELLOW),
    LONG(4.0, NamedTextColor.RED);

    override fun color() = color
}