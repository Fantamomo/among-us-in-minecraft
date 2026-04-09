package com.fantamomo.mc.amongus.ai

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.llm.LLMProvider
import com.fantamomo.mc.amongus.data.AmongUsConfig

enum class AiProvider(val llm: LLMProvider) {
    OpenRouter(LLMProvider.OpenRouter) {
        override fun createClient(key: String): LLMClient {
            val config = OpenRouterClientSettings(
                baseUrl = AmongUsConfig.AI.Provider.baseUrl.ifBlank { "https://openrouter.ai" },
                chatCompletionsPath = AmongUsConfig.AI.Provider.chatCompletionsPath.ifBlank { "api/v1/chat/completions" },
                modelsPath = AmongUsConfig.AI.Provider.modelPath.ifBlank { "api/v1/models" },
                embeddingsPath = AmongUsConfig.AI.Provider.embeddingsPath.ifBlank { "api/v1/embeddings" }
            )
            return OpenRouterLLMClient(key, config)
        }
    };

    open fun canBeUsed(): Boolean = true

    abstract fun createClient(key: String): LLMClient

    companion object {
        fun getOrNull(id: String): AiProvider? = entries.find { it.llm.id.equals(id, true) }
    }
}