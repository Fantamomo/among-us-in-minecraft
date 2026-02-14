package com.fantamomo.mc.amongus.settings

import com.fantamomo.mc.amongus.game.Game

class Settings(val game: Game) {
    private val data: MutableMap<String, Any> = mutableMapOf()
    private val recentlyChanged: MutableList<SettingsKey<*, *>> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: SettingsKey<T, *>): T? = data[key.key] as? T

    fun <T : Any> getOrDefault(key: SettingsKey<T, *>, defaultValue: T) = getOrNull(key) ?: defaultValue

    operator fun <T : Any> get(key: SettingsKey<T, *>) = getOrDefault(key, key.defaultValue)

    fun <T : Any> set(key: SettingsKey<T, *>, value: T) {
        addRecentlyChanged(key)
        game.abortStartCooldown()
        data[key.key] = value
    }

    fun remove(key: SettingsKey<*, *>): Boolean {
        addRecentlyChanged(key)
        game.abortStartCooldown()
        return data.remove(key.key) != null
    }

    fun getRecentlyChanged(): List<SettingsKey<*, *>> = recentlyChanged

    private fun addRecentlyChanged(key: SettingsKey<*, *>) {
        if (recentlyChanged.contains(key)) return
        recentlyChanged.addFirst(key)
        if (recentlyChanged.size > 5) recentlyChanged.removeLast()
    }
}