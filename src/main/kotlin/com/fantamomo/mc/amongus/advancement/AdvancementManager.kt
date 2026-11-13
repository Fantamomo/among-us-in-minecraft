package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.util.ticks
import io.ktor.utils.io.*
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
import kotlin.time.Duration

@OptIn(ExperimentalAdvancementApi::class)
object AdvancementManager {
    private val WARNING_SEQUENCE_COUNT = if (AmongUsConstants.IN_DEVELOPMENT) 5 else 15
    private val logger = LoggerFactory.getLogger(AdvancementManager::class.java)
    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    private val players: MutableMap<UUID, Advancements> = mutableMapOf()
    private val dataPath = AmongUs.dataPath.resolve("data").resolve("advancements")

    private val activeSequencesByPlayer = mutableMapOf<UUID, MutableSet<RunningAdvancementSequence>>()

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
                    runSequencedAdvancement(
                        player,
                        advancement,
                        assigned as SequencedAdvancement.AssignedSequencedAdvancement
                    )
                }
            }
        }
    }

    private fun runSequencedAdvancement(
        player: HumanAmongUsPlayer,
        advancement: SequencedAdvancement,
        assigned: SequencedAdvancement.AssignedSequencedAdvancement
    ) {
        if (!advancement.applicableTo(player)) return
        if (assigned.isCompleted) return

        val runner = advancement.runner

        val runningSequence = RunningAdvancementSequence(player, advancement, runner, assigned)
        val playerSequences = activeSequencesByPlayer.getOrPut(player.uuid) { mutableSetOf() }
        if (playerSequences.any { it.runner == runner && !it.finishedFirstAction }) return

        val thisRunnerCount = playerSequences.count { it.runner == runner }
        if (thisRunnerCount > WARNING_SEQUENCE_COUNT) {
            logger.warn(
                "Player $player has more than $WARNING_SEQUENCE_COUNT running sequences for runner $runner ($advancement)" +
                        if (!AmongUsConstants.IN_DEVELOPMENT) " please report this to the plugin author" else " please fix this"
            )
        }
        playerSequences.add(runningSequence)

        runningSequence.start()
    }

    fun gameEnd(game: Game) {
        for (player in game.players) {
            if (player.isBot) continue

            activeSequencesByPlayer.remove(player.uuid)?.clear()
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
        for (player in players) {
            savePlayer(player.key)
        }
        players.clear()
    }

    fun awardAdvancement(advancement: AssignedAdvancement<*, *>) {
        TODO("Not yet implemented")
    }

    fun awardAdvancement(advancement: Advancement<*, *>) {
        TODO("Not yet implemented")
    }

    private class RunningAdvancementSequence(
        val player: HumanAmongUsPlayer,
        val advancement: SequencedAdvancement,
        val runner: AdvancementSequenceRunner,
        val assigned: SequencedAdvancement.AssignedSequencedAdvancement,
    ) : Continuation<Unit> {

        override val context = EmptyCoroutineContext

        val advancementSequence = AdvancementSequence(
            advancement = advancement,
            player = player,
            scheduleTimeout = ::scheduleTimeout,
            cancelEventWait = player.game.eventManager::removeListener,
            onBlockerChange = ::onBlockerChange
        )

        var finishedFirstAction = false

        private fun scheduleTimeout(duration: Duration, callback: () -> Unit): TimeoutHandle {
            var canceled = false
            val task = AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
                if (!canceled) {
                    callback()
                }
            }, duration.ticks)

            return TimeoutHandle {
                canceled = true
                task.cancel()
            }
        }

        private fun onBlockerChange(
            blockers: List<AdvancementSequenceBlocker>,
            element: AdvancementSequenceBlocker,
            removed: Boolean
        ) {
            if (finishedFirstAction) return
            if (blockers.isEmpty() && removed) {
                finishedFirstAction = true
                runSequencedAdvancement(player, advancement, assigned)
            }
        }

        override fun resumeWith(result: Result<Unit>) {
            activeSequencesByPlayer[player.uuid]?.let {
                it.remove(this)
                if (it.isEmpty()) {
                    activeSequencesByPlayer.remove(player.uuid)
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

        fun start() {
            runner.runner.startCoroutine(
                advancementSequence,
                completion = this
            )
        }
    }
}