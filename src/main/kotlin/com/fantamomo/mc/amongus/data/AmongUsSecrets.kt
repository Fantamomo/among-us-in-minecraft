package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

internal object AmongUsSecrets {

    private val path = AmongUs.dataPath.resolve("secrets.properties")
    private val properties = Properties()

    fun init() {
        if (path.notExists()) AmongUs.saveResource("secrets.properties", false)
        try {
            properties.load(path.inputStream())
        } catch (e: Exception) {
            AmongUs.slF4JLogger.error("Failed to load secrets.properties", e)
        }
    }

    val MINE_SKIN_API_KEY: String
        get() = properties.getProperty("mineskin") ?: ""
}