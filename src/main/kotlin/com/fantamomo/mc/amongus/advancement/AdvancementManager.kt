package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.humanOrNull
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import com.fantamomo.mc.amongus.util.ticks
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration

@OptIn(ExperimentalAdvancementApi::class)
object AdvancementManager {
    private val logger = LoggerFactory.getLogger(AdvancementManager::class.java)
    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    private val players: MutableMap<UUID, Advancements> = mutableMapOf()
    private val dataPath = AmongUs.dataPath.resolve("data").resolve("advancements")

    private val supervisorsByGame = mutableMapOf<Game, MutableList<PlayerSequence>>()

    /** Attempts of advancements with [Advancement.hasGameAdvantage], they are only recorded after the game. */
    private val pendingAttemptsByGame = mutableMapOf<Game, MutableList<() -> Unit>>()
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
                    startSequence(
                        game,
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

    private fun startSequence(
        game: Game,
        player: HumanAmongUsPlayer,
        advancement: SequencedAdvancement,
        assigned: SequencedAdvancement.AssignedSequencedAdvancement
    ) {
        if (assigned.isCompleted) return
        val sequence = PlayerSequence(game, player, advancement, assigned)
        supervisorsByGame.getOrPut(game) { mutableListOf() }.add(sequence)
        try {
            sequence.supervisor.start()
        } catch (e: Exception) {
            logger.error("Failed to start the sequence of advancement ${advancement.id} for player ${player.uuid}", e)
            sequence.supervisor.stop()
        }
    }

    fun gameEnd(game: Game) {
        activeListenersByGame.remove(game)?.forEach(::stopListener)
        supervisorsByGame.remove(game)?.forEach { it.supervisor.stop() }
        pendingAttemptsByGame.remove(game)?.forEach { it() }

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
        // e.g. awarded from code while attempts are still running
        for (sequences in supervisorsByGame.values) {
            sequences.filter { it.assigned === advancement }.forEach { it.supervisor.stop() }
        }

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

    /**
     * The attempts of one sequenced advancement for one player during one game.
     */
    private class PlayerSequence(
        val game: Game,
        override val player: HumanAmongUsPlayer,
        val advancement: SequencedAdvancement,
        val assigned: SequencedAdvancement.AssignedSequencedAdvancement,
    ) : SequenceHost, SequenceSupervisor.Listener {

        val supervisor = SequenceSupervisor(advancement.runner, this, this)

        override fun <E : AmongUsEvent> listen(type: Class<E>, handler: (E) -> Unit): Registration {
            val eventManager = game.eventManager
            eventManager.register(type, handler)
            @Suppress("UNCHECKED_CAST")
            return Registration { eventManager.unregister(handler as (AmongUsEvent) -> Unit) }
        }

        override fun schedule(delay: Duration, action: () -> Unit): Registration {
            var cancelled = false
            val task = AmongUs.server.scheduler.runTaskLater(AmongUs, { -> if (!cancelled) action() }, delay.ticks)
            return Registration {
                cancelled = true
                task.cancel()
            }
        }

        override fun repeat(period: Duration, action: () -> Unit): Registration {
            var cancelled = false
            val ticks = period.ticks.coerceAtLeast(1)
            val task = AmongUs.server.scheduler.runTaskTimer(AmongUs, { -> if (!cancelled) action() }, ticks, ticks)
            return Registration {
                cancelled = true
                task.cancel()
            }
        }

        override fun canStart() = !assigned.isCompleted && advancement.applicableTo(player)

        override fun onAttemptFinished(sequence: AdvancementSequence, result: Result<Unit>) {
            val exception = result.exceptionOrNull()
            // a stopped attempt (game end) did not really fail, it can only improve the best attempt
            val counts = exception !is SequenceStoppedException
            val completedSteps = sequence.completedSteps.toSet()
            val failedStep = sequence.failedStep?.key
            if (AmongUsConstants.IN_DEVELOPMENT) {
                logger.info("Attempt of ${advancement.id} by ${player.name} ended: ${exception ?: "success"} (failed step: $failedStep, completed: $completedSteps)")
            }
            val record = { assigned.recordAttempt(completedSteps, failedStep, counts) }
            // the progress could reveal information during the game (e.g. that there were two witnesses)
            if (advancement.hasGameAdvantage) pendingAttemptsByGame.getOrPut(game) { mutableListOf() }.add(record)
            else record()
        }

        override fun onError(sequence: AdvancementSequence, exception: Throwable) {
            logger.error("Attempt of advancement ${advancement.id} by ${player.name} failed unexpectedly (${sequence.describe()})", exception)
        }

        override fun onCompleted(sequence: AdvancementSequence) {
            awardAdvancement(assigned)
        }

        override fun onWarning(reason: String) {
            logger.warn("Sequence of advancement ${advancement.id} (player ${player.name}): $reason" +
                    if (AmongUsConstants.IN_DEVELOPMENT) "" else ", please report this to the plugin author")
        }
    }
}
