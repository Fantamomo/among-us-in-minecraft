package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.command.arguments.DurationArgumentType
import com.fantamomo.mc.amongus.settings.SettingsType
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.time.Duration

class DurationSettingsType(min: Duration?, max: Duration?) : SettingsType<Duration> {
    override val type = Duration::class
    override val argumentType = DurationArgumentType(min, max)
    override fun itemRepresentation(value: Duration) = ItemStack(Material.CLOCK)

    override fun onItemClick(current: Duration): Duration {
        TODO("Not yet implemented")
    }

    companion object {
        val positive = DurationSettingsType(Duration.ZERO, null)
        val negative = DurationSettingsType(null, Duration.ZERO)
        fun range(min: Duration, max: Duration) = DurationSettingsType(min, max)
        fun min(min: Duration) = DurationSettingsType(min, null)
        fun max(max: Duration) = DurationSettingsType(null, max)
    }
}