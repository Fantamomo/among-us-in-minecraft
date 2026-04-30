package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

/**
 * Singleton object responsible for loading and providing sensitive configuration secrets
 * required by the AmongUs application. Secrets are fetched from a properties file
 * located in the application's data folder.
 *
 * @author Fantamomo
 */
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

    /**
     * The API key used to access the MineSkin API.
     *
     * @see com.fantamomo.mc.amongus.util.internal.MorphSkinManager
     */
    val MINE_SKIN_API_KEY: String
        get() = properties.getProperty("mineskin")?.trim() ?: ""

    /**
     * The API key used to access an AI provider service.
     *
     * @see com.fantamomo.mc.amongus.ai.AiService
     */
    val AI_PROVIDER_KEY: String
        get() = properties.getProperty("ai")?.trim() ?: ""
}