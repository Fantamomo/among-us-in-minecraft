package com.fantamomo.mc.amongus.statistics

import com.fantamomo.mc.amongus.AmongUs
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.uuid.Uuid

object StatisticsManager {
    private val directory = AmongUs.dataPath.resolve("statistics")
    private val statistics: MutableList<StatisticMap> = mutableListOf()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val logger = LoggerFactory.getLogger("AmongUs-StatisticsManager")

    init {
        // Trigger one serialization to force initialization of all
        // kotlinx.serialization related classes that are required at runtime.
        //
        // This prevents a java.lang.NoClassDefFoundError that can occur when the
        // plugin JAR is hot-reloaded or replaced while the plugin is still loaded,
        // because some serialization classes would otherwise be loaded lazily
        // only on first real use.
        val value = StatisticMap("dummy-group", Uuid.random()).apply {
            average("average")
            counter("counter")
            list("list")
            timer("timer")
        }
        json.encodeToString(value)
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
            return json.decodeFromString<StatisticMap>(text)
        } catch (e: Exception) {
            logger.error("Failed to load statistic map from file {}", file, e)
            return null
        }
    }

    fun saveAll() {
        statistics.forEach(::save)
    }

    fun save(statistic: StatisticMap) {
        val file = directory.resolve(statistic.group).resolve("${statistic.id}.json")
        try {
            val json = json.encodeToString(statistic)
            file.writeText(json)
        } catch (e: Exception) {
            logger.error("Failed to save statistic map to file {}", file, e)
        }
    }
}