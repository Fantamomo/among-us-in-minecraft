package com.fantamomo.mc.amongus.util.data

import com.fantamomo.mc.amongus.util.Colored
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

enum class TaskBarUpdateEnum(private val color: TextColor) : Colored{
    IMMEDIATELY(NamedTextColor.GREEN),
    MEETING(NamedTextColor.YELLOW),
    NONE(NamedTextColor.RED);

    override fun color() = color
}