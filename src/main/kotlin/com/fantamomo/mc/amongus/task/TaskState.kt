package com.fantamomo.mc.amongus.task

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

enum class TaskState(
    val color: TextColor,
    scoreboardIcon: String,
    iconColor: TextColor = color
) {
    INCOMPLETE(NamedTextColor.GRAY, "scoreboard.task.incomplete", NamedTextColor.RED),
    IN_PROGRESS(NamedTextColor.YELLOW, "scoreboard.task.in_progress"),
    COMPLETED(NamedTextColor.GREEN, "scoreboard.task.completed");

    val numberFormat: NumberFormat = NumberFormat.fixed(
        Component.translatable(
            scoreboardIcon,
            Style.style(iconColor, TextDecoration.BOLD)
        )
    )

    operator fun component1() = color
    operator fun component2() = numberFormat
}