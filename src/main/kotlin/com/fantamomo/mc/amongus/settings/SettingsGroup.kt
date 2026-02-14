package com.fantamomo.mc.amongus.settings

import org.bukkit.Material

open class SettingsGroup(val name: String, val material: Material) {
    internal val settings: MutableSet<SettingsKey<*, *>> = mutableSetOf()
    val displayName: String = "settings.group.name.$name"
    val displayDescription: String = "settings.group.description.$name"

    val keys: Set<SettingsKey<*, *>> = settings

    fun <T : Any, S : SettingsType<T>> key(
        key: String,
        type: S,
        defaultValue: T
    ) = SettingsKey(
        key,
        type,
        defaultValue,
        group = this
    )
}