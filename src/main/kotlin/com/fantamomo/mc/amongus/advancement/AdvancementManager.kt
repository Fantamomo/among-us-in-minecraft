package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.util.ticks
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object AdvancementManager {
    private val logger = LoggerFactory.getLogger(AdvancementManager::class.java)
    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    private val players: MutableMap<UUID, Advancements> = mutableMapOf()
    private val dataPath = AmongUs.dataPath.resolve("advancements")

    private val sequenceJobs = mutableMapOf<UUID, MutableSet<Job>>()

    private class Advancements(val uuid: UUID) {
        val advancements: MutableMap<Advancement<*, *>, AssignedAdvancement<*, *>> = mutableMapOf()
    }

    fun init() {

    }

    fun gameStart(game: Game) {
        for (player in game.players) {
            if (player.isBot) continue
            val advancements = loadPlayer0(player.uuid)
            for (advancement in Advancement.advancements) {
                var assigned = advancements.advancements[advancement]
                if (assigned == null) {
                    assigned = advancement.new(player.uuid)
                    advancements.advancements[advancement] = assigned
                }
                if (assigned is ListenerAdvancement.AssignedListenerAdvancement) {
                    assigned.start(player)
                }
                if (advancement is SequencedAdvancement) {
                    runSequencedAdvancement(player, advancement, assigned as SequencedAdvancement.AssignedSequencedAdvancement)
                }
            }
        }
    }

    private fun runSequencedAdvancement(player: HumanAmongUsPlayer, advancement: SequencedAdvancement, assigned: SequencedAdvancement.AssignedSequencedAdvancement) {
        val job = Job()
        sequenceJobs.getOrPut(player.uuid) { mutableSetOf() }.add(job)
        val advancementSequence = AdvancementSequence(
            advancement = advancement,
            player = player,
            job = job,
            scheduleTimeout = { duration, callback ->
                val task = AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
                    callback()
                }, duration.ticks)

                TimeoutHandle(task::cancel)
            },
            cancelEventWait = { continuation ->
                player.game.eventManager.removeListener(continuation)
            }
        )
        advancement.runner.runner.startCoroutine(
            advancementSequence,
            completion = object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    sequenceJobs[player.uuid]?.let {
                        it.remove(job)
                        if (it.isEmpty()) {
                            sequenceJobs.remove(player.uuid)
                        }
                    }
                    val throwable = result.exceptionOrNull()
                    if (throwable == null) {
                        logger.info("Advancement sequence completed ($advancement) successfully") // TODO: remove success log
                    } else if (throwable !is CancellationException) {
                        logger.error("Advancement sequence failed ($advancement)", throwable)
                    }
                    runSequencedAdvancement(player, advancement, assigned)
                }
            }
        )
    }

    fun gameEnd(game: Game) {
        for (player in game.players) {
            if (player.isBot) continue

            sequenceJobs.remove(player.uuid)?.clear()
            savePlayer(player.uuid)
        }
    }

    fun savePlayer(uuid: UUID) {
        val advancements = players[uuid] ?: return
        val file = dataPath.resolve("$uuid.json")
        try {
            file.outputStream().use { outputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                json.encodeToStream(buildJsonObject {
                    for ([advancement, data] in advancements.advancements) {
                        try {
                            put(advancement.id, data.toJson())
                        } catch (e: Exception) {
                            logger.error("Failed to save advancement ${advancement.id} for player $uuid", e)
                        }
                    }
                }, outputStream)
            }
        } catch (e: Exception) {
            logger.error("Failed to save advancements for player $uuid", e)
        }
    }


    fun loadPlayer(uuid: UUID) {
        loadPlayer0(uuid)
    }

    private fun loadPlayer0(uuid: UUID): Advancements {
        if (players.containsKey(uuid)) return players[uuid]!!
        val file = dataPath.resolve("$uuid.json")
        val advancements = Advancements(uuid)
        players[uuid] = Advancements(uuid)
        if (file.exists()) {
            val inputStream = try {
                file.inputStream()
            } catch (e: Exception) {
                logger.error("Failed to load advancements for player $uuid", e)
                return advancements
            }
            try {
                @OptIn(ExperimentalSerializationApi::class)
                val playerData = json.decodeFromStream<JsonObject>(inputStream)
                for ([id, element] in playerData) {
                    val advancement = Advancement.advancementMap[id]
                    if (advancement == null) {
                        logger.warn("Found unknown advancement $id by $uuid")
                    } else {
                        try {
                            val data = advancement.createOf(uuid, element)
                            advancements.advancements[advancement] = data
                        } catch (e: Exception) {
                            logger.error("Failed to load advancement $id for player $uuid", e)
                        }
                    }
                }

            } catch (e: Exception) {
                logger.error("Failed to load advancements for player $uuid", e)
            }
        }
        return advancements
    }


    fun stop() {

    }
}