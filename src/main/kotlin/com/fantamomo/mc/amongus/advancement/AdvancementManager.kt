package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.isBot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object AdvancementManager {
    private val logger = LoggerFactory.getLogger(AdvancementManager::class.java)
    private val players: MutableMap<UUID, Advancements> = mutableMapOf()
    private val dataPath = AmongUs.dataPath.resolve("advancements")
    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

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
                    assigned.start()
                }
                if (advancement is SequencedAdvancement) {
                    runSequencedAdvancement(advancement, assigned as SequencedAdvancement.AssignedSequencedAdvancement)
                }
            }
        }
    }

    private fun runSequencedAdvancement(sequence: SequencedAdvancement, assigned: SequencedAdvancement.AssignedSequencedAdvancement) {

    }

    fun gameEnd(game: Game) {
        for (player in game.players) {
            if (player.isBot) continue
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