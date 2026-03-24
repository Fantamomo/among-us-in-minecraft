package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import java.util.*
import kotlin.io.path.notExists
import kotlin.io.path.readLines

object AmongUsDebug {
    const val DEBUG_FILE_NAME = "IN_DEVELOPMENT"

    private val debugValues: Set<DebugValues> by lazy(::load)

    fun isEnabled() = AmongUsConstants.IN_DEVELOPMENT

    fun isEnabled(value: DebugValues) = isEnabled() && debugValues.contains(value)

    private fun load(): Set<DebugValues> {
        if (!isEnabled()) return emptySet()
        val path = AmongUs.dataPath.resolve(DEBUG_FILE_NAME)
        if (path.notExists()) return emptySet()
        return try {
            path.readLines()
                .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() && !it.startsWith('#') } }
                .mapNotNull { DebugValues.getOrNull(it) }
                .run { if (isEmpty()) emptySet() else EnumSet.copyOf(this) }
        } catch (_: Exception) {
            // ignore any exceptions
            emptySet()
        }
    }

    enum class DebugValues {
        BOT_SHOW_ZOMBIE,
        BOT_SHOW_PATH,
        BOT_SHOW_GOALS;

        fun isEnabled() = isEnabled(this)

        companion object {
            fun getOrNull(value: String): DebugValues? = runCatching { valueOf(value) }.getOrNull()
        }
    }
}