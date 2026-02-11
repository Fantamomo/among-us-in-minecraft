package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.PlayerStatistics
import com.fantamomo.mc.amongus.statistics.*
import com.fantamomo.mc.brigadier.*
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.toKotlinUuid

val AmongUsCommand = paperCommand("amongus") {
    literal("stats") {
        guard {
            val sender = source.sender as? Player
            if (sender == null) {
                sendMessage {
                    translatable("command.error.statistics.not_player")
                }
                return@guard abort()
            }
            if (PlayerManager.getPlayer(sender) != null) {
                sendMessage {
                    translatable("command.error.statistics.in_game")
                }
                return@guard abort()
            }
            val map = StatisticsManager.createOrLoad("player", sender.uniqueId.toKotlinUuid())
            setArgument("map", map)
            continueCommand()
        }
        for ((key, type) in PlayerStatistics.statistics) {
            literal(key) {
                guard {
                    val map = arg<StatisticMap>("map")
                    val value = map.get(key)
                    if (value == null) {
                        sendMessage {
                            translatable("command.error.statistics.no_data") {
                                args {
                                    string("key", key)
                                }
                            }
                        }
                        return@guard abort()
                    }
                    setArgument("stats", value)

                    if (value is ListStatistic && value.isEmpty()) {
                        sendMessage {
                            translatable("command.error.statistics.list.empty") {
                                args {
                                    string("key", key)
                                }
                            }
                        }
                        return@guard abort()
                    }
                    continueCommand()
                }
                when (type) {
                    AverageStatistic::class -> execute {
                        val stats = arg<AverageStatistic>("stats")
                        sendMessage {
                            translatable("command.success.statistics.average") {
                                args {
                                    numeric("average", stats.value)
                                }
                            }
                        }
                        SINGLE_SUCCESS
                    }

                    CounterStatistic::class -> execute {
                        val stats = arg<CounterStatistic>("stats")
                        sendMessage {
                            translatable("command.success.statistics.counter") {
                                args {
                                    numeric("count", stats.value)
                                }
                            }
                        }
                        SINGLE_SUCCESS
                    }

                    TimerStatistic::class -> execute {
                        val stats = arg<TimerStatistic>("stats")
                        sendMessage {
                            translatable("command.success.statistics.timer") {
                                args {
                                    string("time", stats.totalMillis.milliseconds.toString())
                                }
                            }
                        }
                        SINGLE_SUCCESS
                    }

                    ListStatistic::class -> {
                        literalExecute("min") {
                            val stats = arg<ListStatistic>("stats")
                            sendMessage {
                                translatable("command.success.statistics.list.min") {
                                    args {
                                        numeric("min", stats.min)
                                    }
                                }
                            }
                            SINGLE_SUCCESS
                        }
                        literalExecute("max") {
                            val stats = arg<ListStatistic>("stats")
                            sendMessage {
                                translatable("command.success.statistics.list.max") {
                                    args {
                                        numeric("max", stats.max)
                                    }
                                }
                            }
                            SINGLE_SUCCESS
                        }
                        literalExecute("average") {
                            val stats = arg<ListStatistic>("stats")
                            sendMessage {
                                translatable("command.success.statistics.list.average") {
                                    args {
                                        numeric("average", stats.average)
                                    }
                                }
                            }
                            SINGLE_SUCCESS
                        }
                        literalExecute("count") {
                            val stats = arg<ListStatistic>("stats")
                            sendMessage {
                                translatable("command.success.statistics.list.count") {
                                    args {
                                        numeric("count", stats.data.size)
                                    }
                                }
                            }
                            SINGLE_SUCCESS
                        }
                    }
                }
            }
        }
    }
}