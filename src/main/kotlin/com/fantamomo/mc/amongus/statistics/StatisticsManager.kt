package com.fantamomo.mc.amongus.statistics

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.uuid.Uuid

object StatisticsManager {
    private val directory = AmongUs.dataPath.resolve("statistics")
    private val statistics: MutableList<StatisticMap> = mutableListOf()
    private val json = Json {
        prettyPrint = AmongUs.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }
    internal val logger = LoggerFactory.getLogger("AmongUs-StatisticsManager")

    init {
        // Register built-in statistic types
        StatisticRegistry.register("average", AverageStatistic.Companion::fromJson)
        StatisticRegistry.register("list", ListStatistic.Companion::fromJson)
        StatisticRegistry.register("timer", TimerStatistic.Companion::fromJson)
        StatisticRegistry.register("counter", CounterStatistic.Companion::fromJson)

        // Trigger one serialization to force initialization of all
        // kotlinx.serialization related classes that are required at runtime.
        //
        // This prevents a java.lang.NoClassDefFoundError that can occur when the
        // plugin JAR is hot-reloaded or replaced while the plugin is still loaded,
        // because some serialization classes would otherwise be loaded lazily
        // only on first real use.
        json.encodeToString(StatisticMap("dummy-group", Uuid.random()).toJson())
    }

    fun register(statistic: StatisticMap) {
        statistics.add(statistic)
    }

    fun unregister(statistic: StatisticMap) {
        statistics.remove(statistic)
    }

    fun createOrLoad(group: String, id: Uuid): StatisticMap {
        var statisticMap = statistics.firstOrNull { it.group == group && it.id == id }
        if (statisticMap != null) return statisticMap
        statisticMap = load(group, id) ?: StatisticMap(group, id)
        register(statisticMap)
        return statisticMap
    }

    private fun load(group: String, id: Uuid): StatisticMap? {
        StatisticMap.checkGroup(group)
        val file = directory.resolve(group).resolve("$id.json")
        if (file.notExists()) return null
        try {
            val text = file.readText()
            val jsonObject = json.parseToJsonElement(text).jsonObject
            return StatisticMap.fromJson(group, id, jsonObject)
        } catch (e: Exception) {
            logger.error("Failed to load statistic map from file {}", file, e)
            return null
        }
    }

    fun saveAll() {
        statistics.forEach(::save)
    }

    fun save(statistic: StatisticMap) {
        val file = directory.resolve(statistic.group).safeCreateDirectories().resolve("${statistic.id}.json")
        try {
            val jsonObject = statistic.toJson()
            file.writeText(json.encodeToString(jsonObject))
        } catch (e: Exception) {
            logger.error("Failed to save statistic map to file {}", file, e)
        }
    }
}