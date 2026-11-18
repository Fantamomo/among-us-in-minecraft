package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.advancement.utils.step.StepFailedException
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.humanOrNull
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import com.fantamomo.mc.amongus.util.ticks
import io.ktor.utils.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
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
    private val earnedAdvancementsByGame = mutableMapOf<Game, MutableList<Pair<UUID, Advancement<*, *>>>>()
    private val gamePlayersByGame = mutableMapOf<Game, MutableSet<UUID>>()
    private val activeListenersByGame = mutableMapOf<Game, MutableSet<ListenerAdvancement.AssignedListenerAdvancement>>()

    private class Advancements(val uuid: UUID) {
        val advancements: MutableMap<Advancement<*, *>, AssignedAdvancement<*, *>> = mutableMapOf()

        fun <A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> get(advancement: A): AA? {
            @Suppress("UNCHECKED_CAST")
            return advancements[advancement] as? AA
        }

        fun <A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> getOrCreate(advancement: A): AA {
            @Suppress("UNCHECKED_CAST")
            return advancements.getOrPut(advancement) { advancement.new(uuid) } as AA
        }
    }

    fun init() {

    }

    fun gameStart(game: Game) {
        val playerSet = gamePlayersByGame.getOrPut(game) { mutableSetOf() }
        for (player in game.players) {
            if (player.isBot) continue
            playerSet.add(player.uuid)
            val advancements = getOrLoad(player.uuid)
            for (advancement in Advancement.advancements) {
                var assigned = advancements.advancements[advancement]
                if (assigned == null) {
                    assigned = advancement.new(player.uuid)
                    advancements.advancements[advancement] = assigned
                }
                if (assigned is ListenerAdvancement.AssignedListenerAdvancement) {
                    startListener(game, player, advancement, assigned)
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

    private fun startListener(
        game: Game,
        player: HumanAmongUsPlayer,
        advancement: Advancement<*, *>,
        assigned: AssignedAdvancement<*, *>,
    ) {
        if (assigned !is ListenerAdvancement.AssignedListenerAdvancement) return
        if (assigned.isCompleted) return
        if (advancement is ListenerAdvancement && !advancement.shouldCreateAdvancementStateOnGameStart(player)) return
        try {
            assigned.start(player)
        } catch (e: Exception) {
            logger.error("Failed to start listener of advancement ${advancement.id} for player ${player.uuid}", e)
            stopListener(assigned)
            return
        }
        // the advancement could have been completed while starting (e.g. by mirroring a statistic)
        if (assigned.isCompleted) stopListener(assigned)
        else activeListenersByGame.getOrPut(game) { mutableSetOf() }.add(assigned)
    }

    private fun stopListener(assigned: ListenerAdvancement.AssignedListenerAdvancement) {
        for (listeners in activeListenersByGame.values) listeners.remove(assigned)
        try {
            assigned.stop()
        } catch (e: Exception) {
            logger.error("Failed to stop listener advancement $assigned", e)
        }
    }

    private fun runSequencedAdvancement(
        player: HumanAmongUsPlayer,
        advancement: SequencedAdvancement,
        assigned: SequencedAdvancement.AssignedSequencedAdvancement
    ) {
        if (!advancement.applicableTo(player)) return
        if (assigned.isCompleted) return

        val goingThough = Advancement.goingThroughSequences[advancement]
        if (goingThough != null) {
            val value = goingThough.get()
            if (value == Advancement.GOING_THROUGH_SEQUENCES_DISABLED) return
            if (value > Advancement.GOING_THROUGH_SEQUENCES_LIMIT) {
                logger.warn("Sequence runner of $advancement is running without any blocker, please report this to the plugin author (we disabled the advancement, because $value is to high)")
                goingThough.set(Advancement.GOING_THROUGH_SEQUENCES_DISABLED)
            } else if (AmongUsConstants.IN_DEVELOPMENT || value > Advancement.GOING_THROUGH_SEQUENCES_WARNING) {
                logger.warn("Sequence runner of $advancement is running without any blocker (currently at $value), please report this to the plugin author")
            }
        }

        val runner = advancement.runner

        val runningSequence = RunningAdvancementSequence(player, advancement, runner, assigned, assigned.startedParts, assigned.completedParts)
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
        activeListenersByGame.remove(game)?.forEach(::stopListener)

        val earned = earnedAdvancementsByGame.remove(game) ?: emptyList()
        val allGamePlayerUuids = gamePlayersByGame.remove(game) ?: emptySet()

        for ([earningUuid, definition] in earned) {
            val earningPlayer = Bukkit.getPlayer(earningUuid)
            val earningName = earningPlayer?.name ?: Bukkit.getOfflinePlayer(earningUuid).name ?: "Player"

            if (definition.hasGameAdvantage) {
                earningPlayer?.sendMessage(createAdvancementMessageSelf(definition))
            }

            val otherMessage = createAdvancementMessageOther(earningName, definition)
            for (otherUuid in allGamePlayerUuids) {
                if (otherUuid != earningUuid) {
                    Bukkit.getPlayer(otherUuid)?.sendMessage(otherMessage)
                }
            }
        }

        for (uuid in allGamePlayerUuids) {
            activeSequencesByPlayer.remove(uuid)?.clear()
            savePlayer(uuid)
        }
    }

    fun savePlayer(uuid: UUID) {
        val advancements = players[uuid] ?: return
        dataPath.safeCreateDirectories()
        val file = dataPath.resolve("$uuid.json")
        try {
            file.outputStream().use { outputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                json.encodeToStream(buildJsonArray {
                    for ([advancement, assignedAdvancement] in advancements.advancements) {
                        try {
                            addJsonObject {
                                put("id", advancement.id)
                                put("is_completed", assignedAdvancement.isCompleted)
                                val data = assignedAdvancement.toJson()
                                if (data != JsonNull) put("data", data)
                            }
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
        getOrLoad(uuid)
    }

    private fun getOrLoad(uuid: UUID): Advancements {
        if (players.containsKey(uuid)) return players[uuid]!!
        val file = dataPath.resolve("$uuid.json")
        val advancements = Advancements(uuid)
        players[uuid] = advancements
        if (file.exists()) {
            val inputStream = try {
                file.inputStream()
            } catch (e: Exception) {
                logger.error("Failed to load advancements for player $uuid", e)
                return advancements
            }
            try {
                @OptIn(ExperimentalSerializationApi::class)
                val playerData = json.decodeFromStream<JsonArray>(inputStream)
                for (element in playerData) {
                    if (element !is JsonObject) {
                        logger.warn("Found invalid advancement data for player $uuid: $element")
                        continue
                    }
                    val id = (element["id"] as? JsonPrimitive)?.contentOrNull
                    if (id == null) {
                        logger.warn("Missing id in advancement data for player $uuid: $element")
                        continue
                    }
                    val isCompleted = (element["is_completed"] as? JsonPrimitive)?.booleanOrNull ?: false
                    val data = element["data"]
                    val advancement = Advancement.advancementMap[id]
                    if (advancement == null) {
                        logger.warn("Found unknown advancement $id by $uuid")
                    } else {
                        try {
                            val assignedAdvancement =
                                if (data != null) advancement.createOf(uuid, data) else advancement.new(uuid)
                            @Suppress("DEPRECATION_ERROR")
                            assignedAdvancement.isCompleted = isCompleted
                            advancements.advancements[advancement] = assignedAdvancement
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
        loadPlayer(advancement.player)
        if (advancement.isCompleted) return
        @Suppress("DEPRECATION_ERROR")
        advancement.isCompleted = true

        if (advancement is ListenerAdvancement.AssignedListenerAdvancement) stopListener(advancement)

        val definition = advancement.definition
        logger.info("Awarding advancement ${definition.id} to player ${advancement.player}")

        val auPlayer = PlayerManager.getPlayer(advancement.player)
        val game = auPlayer?.game

        if (game != null) {
            earnedAdvancementsByGame.getOrPut(game) { mutableListOf() }.add(advancement.player to definition)
            gamePlayersByGame.getOrPut(game) { mutableSetOf() }.add(advancement.player)

            if (!definition.hasGameAdvantage) {
                auPlayer.humanOrNull?.player?.sendMessage(createAdvancementMessageSelf(definition))
                    ?: Bukkit.getPlayer(advancement.player)?.sendMessage(createAdvancementMessageSelf(definition))
            }
        } else {
            auPlayer?.humanOrNull?.player?.sendMessage(createAdvancementMessageSelf(definition))
                ?: Bukkit.getPlayer(advancement.player)?.sendMessage(createAdvancementMessageSelf(definition))
        }
    }

    fun awardAdvancement(player: HumanAmongUsPlayer, advancement: Advancement<*, *>) {
        val assignedAdvancement = getOrLoad(player.uuid)
            .getOrCreate(advancement.castHelper())
        awardAdvancement(assignedAdvancement)
    }

    fun <A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> getAssigned(uuid: UUID, advancement: A): AA =
        getOrLoad(uuid).getOrCreate(advancement)

    fun getAdvancementsForPlayer(uuid: UUID): List<Pair<Advancement<*, *>, AssignedAdvancement<*, *>>> {
        val playerAdvancements = getOrLoad(uuid)
        return Advancement.advancements.map { adv ->
            val assigned = playerAdvancements.advancements[adv]
                ?: adv.new(uuid).also { playerAdvancements.advancements[adv] = it }
            adv to assigned
        }
    }

    fun createAdvancementMessageSelf(advancement: Advancement<*, *>): Component {
        return textComponent {
            translatable("advancement.award.self") {
                args {
                    component("name") {
                        append(advancement.name)
                        hoverEvent(KHoverEventType.ShowText) {
                            append(advancement.name)
                            newLine()
                            append(advancement.description)
                        }
                    }
                }
            }
        }
    }

    fun createAdvancementMessageOther(playerName: String, advancement: Advancement<*, *>): Component {
        return textComponent {
            translatable("advancement.award.other") {
                args {
                    string("player", playerName)
                    component("name") {
                        append(advancement.name)
                        hoverEvent(KHoverEventType.ShowText) {
                            append(advancement.name)
                            newLine()
                            append(advancement.descriptionOther)
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    private inline fun <A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> Advancement<A, AA>.castHelper(): A = this as A

    private class RunningAdvancementSequence(
        val player: HumanAmongUsPlayer,
        val advancement: SequencedAdvancement,
        val runner: AdvancementSequenceRunner,
        val assigned: SequencedAdvancement.AssignedSequencedAdvancement,
        val startedParts: MutableSet<String>,
        val completedParts: MutableSet<String>
    ) : Continuation<Unit>, PartChangeListener {

        override val context = EmptyCoroutineContext

        val advancementSequence = AdvancementSequence(
            advancement = advancement,
            player = player,
            scheduleTimeout = ::scheduleTimeout,
            cancelEventWait = player.game.eventManager::removeListener,
            partChangeListener = this
        )

        var finishedFirstAction = false

        var finished = false
        var started = false

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

        override fun onPartAdded(
            parts: List<AdvancementSequencePart>,
            element: AdvancementSequencePart
        ) {
            when (element) {
                is AdvancementSequencePart.Blocking -> {}
                is AdvancementSequencePart.NamedPart -> {
                    startedParts.add(element.key)
                }
            }
        }

        override fun onPartRemoved(
            parts: List<AdvancementSequencePart>,
            element: AdvancementSequencePart,
            exceptional: Boolean
        ) {
            when (element) {
                is AdvancementSequencePart.Blocking -> {
                    if (finishedFirstAction) return
                    if (parts.none { it is AdvancementSequencePart.Blocking }) {
                        finishedFirstAction = true
                        runSequencedAdvancement(player, advancement, assigned)
                    }
                }
                is AdvancementSequencePart.NamedPart -> {
                    completedParts.add(element.key)
                }
            }
        }

        override fun resumeWith(result: Result<Unit>) {
            finished = true
            if (started) {
                val integer = Advancement.goingThroughSequences[advancement]
                if (integer != null && integer.get() != Advancement.GOING_THROUGH_SEQUENCES_DISABLED && integer.addAndGet(-Advancement.GOING_THROUGH_SEQUENCES_DECREMENT) <= 0) {
                    Advancement.goingThroughSequences.remove(advancement)
                }
            }
            activeSequencesByPlayer[player.uuid]?.let {
                it.remove(this)
                if (it.isEmpty()) {
                    activeSequencesByPlayer.remove(player.uuid)
                }
            }
            val throwable = result.exceptionOrNull()
            if (throwable == null) {
                logger.info("Advancement sequence completed ($advancement) successfully") // TODO: remove success log
            } else if (throwable is StepFailedException) {
                logger.info("Step ${throwable.stepId} failed") // TODO: remove after testing
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
            if (finished) {
                val integer = Advancement.goingThroughSequences.putIfAbsent(advancement, AtomicInteger())
                if (integer?.get() != Advancement.GOING_THROUGH_SEQUENCES_DISABLED) {
                    integer?.addAndGet(Advancement.GOING_THROUGH_SEQUENCES_INCREMENT)
                }
            }
            started = true
        }
    }
}