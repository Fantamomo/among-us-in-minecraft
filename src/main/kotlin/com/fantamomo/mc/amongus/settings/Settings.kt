package com.fantamomo.mc.amongus.settings

class Settings {
    private val data: MutableMap<String, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: SettingsKey<T, *>): T? = data[key.key] as? T

    fun <T : Any> getOrDefault(key: SettingsKey<T, *>, defaultValue: T) = getOrNull(key) ?: defaultValue

    operator fun <T : Any> get(key: SettingsKey<T, *>) = getOrDefault(key, key.defaultValue)

    fun <T : Any> set(key: SettingsKey<T, *>, value: T) { data[key.key] = value }

    fun remove(key: SettingsKey<*, *>) = data.remove(key.key) != null
}