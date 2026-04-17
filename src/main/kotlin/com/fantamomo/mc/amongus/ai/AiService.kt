package com.fantamomo.mc.amongus.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import kotlinx.coroutines.flow.Flow
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Logger
import org.slf4j.LoggerFactory

internal object AiService {
    private val logger = LoggerFactory.getLogger("AmongUsAiService")
    private val provider = AmongUsConfig.AI.Provider.type
    private val model = AmongUsConfig.AI.model?.let {
        if (provider == null) return@let null
        LLModel(
            provider.llm,
            it,
            listOf(
                LLMCapability.Temperature,
                LLMCapability.Speculation,
                LLMCapability.Completion
            )
        )
    }

    fun isEnabled() = AmongUsConfig.AI.enabled && AmongUsSecrets.AI_PROVIDER_KEY.isNotBlank() && model != null && provider?.canBeUsed() == true

    private val client = if (isEnabled()) run {
        provider!!.createClient(AmongUsSecrets.AI_PROVIDER_KEY)
    } else null

    private val agent = if (isEnabled()) run {
        AIAgent(
            promptExecutor = MultiLLMPromptExecutor(client!!),
            llmModel = model!!
        )
    } else null

    init {
        // would spawn the console for not necessary infos
        setLevel("ai.koog.agents.core.agent.entity.AIAgentSubgraphBase", Level.WARN)
        setLevel("ai.koog.agents.core.agent.GraphAIAgent", Level.FATAL)
    }

    private fun setLevel(logger: String, level: Level) {
        val logger = LogManager.getLogger(logger) as Logger
        logger.level = level
    }

    suspend fun run(prompt: String): String {
        if (agent == null) throw IllegalStateException("AI is not enabled or properly configured.")
        logger.debug("Running AI with prompt: $prompt")
        return agent.run(prompt)
    }

    fun stream(prompt: String): Flow<StreamFrame> {
        if (client == null) throw IllegalStateException("AI is not enabled or properly configured.")
        return client.executeStreaming(
            prompt = prompt("streaming_prompt") {
                system(prompt)
            },
            model = model!!,
        )
    }
}