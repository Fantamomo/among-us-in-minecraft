package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ai.AiProvider
import com.fantamomo.mc.amongus.util.internal.MorphSkinManager
import com.fantamomo.mc.amongus.util.skinblender.VirusSkinBlender
import org.bukkit.configuration.ConfigurationSection

object AmongUsConfig {

    private val config = AmongUs.config

    open class ConfigSection(vararg val sectionId: String) {
        val section = sectionId.fold(config as ConfigurationSection) { acc, id ->
            acc.getConfigurationSection(id) ?: acc.createSection(id)
        }
    }

    fun init() {
        listOf(MsgCommandBlocker, MorphBlender, Roles, Modifications, GameCreation)
    }

    object MsgCommandBlocker : ConfigSection("msg-command-blocker") {
        val disabled = section.getBoolean("disabled", false)
        val legacy = section.getBoolean("legacy", false)
        val commands = section.getStringList("commands")
    }

    object MorphBlender : ConfigSection("morph-blender") {
        val enabled = section.getBoolean("enabled", false)
        val blender = section.getString("blender", VirusSkinBlender.id)
        val visibility = section.getString("visibility")?.let(MorphSkinManager.Visibility::getOrNull)
            ?: MorphSkinManager.Visibility.AUTO
    }

    object Roles : ConfigSection("roles") {
        val disabled = section.getStringList("disabled").map { it.lowercase() }.toSet()
    }

    object Modifications : ConfigSection("modifications") {
        val disabled = section.getStringList("disabled").map { it.lowercase() }.toSet()
    }

    object GameCreation : ConfigSection("game-creation") {
        val everyoneCanCreate = section.getBoolean("everyone-can-create", false)
        val maxGames = section.getInt("max-games", 10)
        val ignoreAdmins = section.getBoolean("ignore-admins", true)
    }

    object ActionLogUpload : ConfigSection("action-log-upload") {
        val enabled = section.getBoolean("enabled", false)
        val url =
            section.getString("url", "http://localhost:29243") // todo: replace with url of the real server when it's up
        val sendToPlayers = section.getBoolean("send-to-players", true)
    }

    object AI : ConfigSection("ai") {
        val enabled = section.getBoolean("enabled", false)

        object Provider : ConfigSection("ai", "provider") {
            val type = section.getString("type", "openrouter")?.let { AiProvider.getOrNull(it) }
            val baseUrl = section.getString("base-url", "").orEmpty()
            val chatCompletionsPath = section.getString("chat-completions-path", "").orEmpty()
            val modelPath = section.getString("model-path", "").orEmpty()
            val embeddingsPath = section.getString("embeddings-path", "").orEmpty()
        }
        val model = section.getString("model", "qwen/qwen3-32b")

        val generateGameSummary = section.getBoolean("generate-game-summary", true)
    }

    val animateScoreboard = config.getBoolean("animate-scoreboard", true)
}