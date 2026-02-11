package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.command.arguments.DurationArgumentType
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.settings.SettingsType
import com.fantamomo.mc.amongus.util.toSmartString
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DurationSettingsType(
    val min: Duration?,
    val max: Duration?
) : SettingsType<Duration> {
    override val type = Duration::class
    override val argumentType = DurationArgumentType(min, max)
    @Suppress("UnstableApiUsage")
    override fun itemRepresentation(value: Duration) = ItemStack(Material.CLOCK).apply {
        if (min == null && max == null) return@apply
        val lore = ItemLore.lore()
        if (min != null) {
            lore.addLine(textComponent {
                translatable("setting.ui.type.duration.min") {
                    args {
                        string("min", min.toSmartString())
                    }
                }
            })
        }
        if (max != null) {
            lore.addLine(textComponent {
                translatable("setting.ui.type.duration.max") {
                    args {
                        string("max", max.toSmartString())
                    }
                }
            })
        }
        setData(DataComponentTypes.LORE, lore)
    }

    override fun stringRepresentation(value: Duration) = value.toSmartString()

    override fun componentRepresentation(value: Duration) = super.componentRepresentation(value).color(NamedTextColor.BLUE)

    override fun onItemClick(current: Duration, action: ClickType): Duration {
        val candidate = when (action) {
            ClickType.LEFT -> current + 1.seconds
            ClickType.RIGHT -> current - 1.seconds
            ClickType.SHIFT_LEFT -> current + 10.seconds
            ClickType.SHIFT_RIGHT -> current - 10.seconds
            else -> current + 1.seconds
        }

        val clampedMin = min?.let { if (candidate < it) it else candidate } ?: candidate
        val clamped = max?.let { if (clampedMin > it) it else clampedMin } ?: clampedMin
        return clamped
    }

    companion object {
        val positive = DurationSettingsType(Duration.ZERO, null)
        val negative = DurationSettingsType(null, Duration.ZERO)
        fun range(min: Duration, max: Duration) = DurationSettingsType(min, max)
        fun min(min: Duration) = DurationSettingsType(min, null)
        fun max(max: Duration) = DurationSettingsType(null, max)
    }
}