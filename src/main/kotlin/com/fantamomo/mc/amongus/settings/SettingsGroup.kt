package com.fantamomo.mc.amongus.settings

import net.kyori.adventure.text.Component
import org.bukkit.Material

open class SettingsGroup(
    val name: String,
    val material: Material,
    val parent: SettingsGroup? = null,
    val useCustomName: Boolean = false
) {
    internal val directKeys: MutableList<SettingsKey<*, *>> = mutableListOf()
    val subGroups: MutableList<SettingsGroup> = mutableListOf()

    val displayName: String = "settings.group.name.$name"
    val displayDescription: String = "settings.group.description.$name"

    val keys: List<SettingsKey<*, *>> = directKeys

    fun content(): List<Any> = buildList {
        addAll(subGroups)
        addAll(directKeys)
    }

    init {
        parent?.subGroups?.add(this)
    }

    fun <T : Any, S : SettingsType<T>> key(
        key: String,
        type: S,
        defaultValue: T
    ) = SettingsKey(key, type, defaultValue, group = this)

    fun <T : Any, S : SettingsType<T>> key(
        key: String,
        type: S,
        defaultValue: T,
        displayName: Component,
        description: Component?
    ) = SettingsKey(key, type, defaultValue, displayName, description, this)
}