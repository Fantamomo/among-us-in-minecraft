package com.fantamomo.mc.amongus.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import com.fantamomo.mc.amongus.util.LatencyMonitor
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
internal object AiService {
    private val logger = LoggerFactory.getLogger("AmongUsAiService")

    private val latencyMonitor = LatencyMonitor(
        hardThreshold = 5.seconds
    )
    private var lastLatencyWarning = 0L

    private val provider = AmongUsConfig.AI.Provider.type
    private val models = AmongUsConfig.AI.model.let { models ->
        if (provider == null) return@let listOf()
        models.map { model ->
            LLModel(
                provider.llm,
                model,
                listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Speculation,
                    LLMCapability.Completion
                )
            )
        }
    }
    private var model = models.firstOrNull()

    private var modelIndex = 0
    private var modelErrors = AtomicInt(0)
    private var switchingMutex = Mutex()
    private var requestWithoutResponse = AtomicInt(0)
    private var lastFail = 0L

    fun isEnabled() =
        AmongUsConfig.AI.enabled && AmongUsSecrets.AI_PROVIDER_KEY.isNotBlank() && model != null && provider?.canBeUsed() == true

    fun isNotAvailable(): Boolean {
        if (requestWithoutResponse.load() < 1) return false
        val timeSinceLastFail = System.currentTimeMillis() - lastFail
        if (timeSinceLastFail > 30_000L) {
            requestWithoutResponse.store(0)
            return false
        } else return true
    }

    private var client = if (isEnabled()) run {
        provider!!.createClient(AmongUsSecrets.AI_PROVIDER_KEY)
    } else null

    private var agent = if (isEnabled()) run {
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

    private suspend fun switchModel() {
        if (switchingMutex.isLocked) return
        switchingMutex.withLock {
            val prevModel = model!!
            modelIndex = (modelIndex + 1) % models.size
            model = models[modelIndex]

            modelErrors.store(0)

            client = provider!!.createClient(AmongUsSecrets.AI_PROVIDER_KEY)
            agent = AIAgent(
                promptExecutor = MultiLLMPromptExecutor(client!!),
                llmModel = model!!
            )

            logger.warn("Requested AI model failed to often, switching to next model")
            logger.warn("Prev: ${prevModel.id}, new: ${model!!.id}, index: $modelIndex")
            logger.warn("Please check you AI credits and model availability")
        }
    }

    private fun setLevel(logger: String, level: Level) {
        val logger = LogManager.getLogger(logger) as Logger
        logger.level = level
    }

    suspend fun run(prompt: String): String {
        val agent = agent ?: throw IllegalStateException("AI is not enabled or properly configured.")
        logger.debug("Running AI with prompt: $prompt")
        try {
            val response = agent.run(prompt)
            modelErrors.decrementAndFetch()
            return response
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (modelErrors.addAndFetch(3) > 10) {
                switchModel()
            }
            throw e
        }
    }

    fun stream(prompt: String, checkTime: Boolean = AmongUsConfig.AI.latencyWarning): Flow<StreamFrame> {
        if (client == null) throw IllegalStateException("AI is not enabled or properly configured.")
        return if (checkTime) streamWithTime(prompt)
        else streamNormal(prompt)
    }

    private fun streamWithTime(prompt: String): Flow<StreamFrame> {
        val start = latencyMonitor.start()
        return flow {
            var receivedAnyFrame = false
            client!!.executeStreaming(
                prompt = prompt("streaming_prompt") {
                    system(prompt)
                },
                model = model!!,
            ).onCompletion { error ->
                if (error != null) {
                    if (error !is CancellationException) {
                        if (modelErrors.addAndFetch(3) > 10) {
                            switchModel()
                        }
                    }
                } else {
                    modelErrors.decrementAndFetch()
                }
                val result = start.end()
                if (result.isHardViolation) {
                    val now = System.currentTimeMillis()
                    if (now - lastLatencyWarning > 100_000 && result.hardViolationCount > 5) {
                        logger.warn("Hard latency violation detected: ${result.hardViolationCount} violations")
                        logger.warn("The latency was violated by the AI taking ${result.latencyMs} ms, which exceeds the hard threshold of ${result.hardThreshold} ms.")
                        logger.warn("Mean latency: ${result.mean} ms, StdDev: ${result.stdDev} ms, Soft threshold: ${result.softThreshold} ms.")
                        logger.warn("Consider switching to a different AI provider or model.")
                        logger.warn("The AI response time should be as short as possible to ensure a smooth gameplay experience.")
                        logger.warn("You can disable this warning by setting the 'ai.latency-warning' config option to false.")
                        lastLatencyWarning = now
                    }
                }
            }.collect { frame ->
                if (frame is StreamFrame.End) {
                    if (!receivedAnyFrame) {
                        logger.warn("AI response was empty without any response")
                        logger.warn("Check your AI credits and model availability")
                        modelErrors.addAndFetch(3)
                        requestWithoutResponse.incrementAndFetch()
                        lastFail = System.currentTimeMillis()
                    } else {
                        requestWithoutResponse.store(0)
                    }
                } else {
                    receivedAnyFrame = true
                }
                emit(frame)
            }
        }
    }

    private fun streamNormal(prompt: String) = client!!.executeStreaming(
        prompt = prompt("streaming_prompt") {
            system(prompt)
        },
        model = model!!,
    )
}