package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.command.arguments.PlayerColorArgumentType
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerColor
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.PlayerStatistics
import com.fantamomo.mc.amongus.statistics.*
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.BoolArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.toKotlinUuid

val AmongUsCommand = paperCommand("amongus") {
    statsCommand()
    colorCommand()
}

private fun PaperCommand.colorCommand() {
    literal("color") {
        argument("color", PlayerColorArgumentType) {
            argument("target", ArgumentTypes.player()) {
                requires { sender.hasPermission(Permissions.SET_PLAYER_COLOR_OTHER) }
                argument("force", BoolArgumentType.bool()) {
                    requires { sender.hasPermission(Permissions.SET_PLAYER_COLOR_FORCE) }
                    setColorCommand()
                }
                setColorCommand()
            }
            setColorCommand()
        }
        literal("random") {
            argument("target", ArgumentTypes.player()) {
                requires { sender.hasPermission(Permissions.SET_PLAYER_COLOR_OTHER) }
                setColorCommand()
            }
            setColorCommand()
        }
    }
}

private fun KtCommandBuilder<CommandSourceStack, *>.setColorCommand() = execute {
    val resolver = optionalArg<PlayerSelectorArgumentResolver>("target")
    var target = resolver?.resolve(source)?.firstOrNull()
    if (target == null) {
        target = source.sender as? Player
        if (target == null) {
            sendMessage {
                translatable("command.error.set_color.not_player")
            }
            return@execute 0
        }
    }
    val amongUsPlayer = PlayerManager.getPlayer(target)
    if (amongUsPlayer == null) {
        sendMessage {
            if (target === source.sender) {
                translatable("command.error.set_color.not_joined")
            } else {
                translatable("command.error.set_color.not_joined.other") {
                    args {
                        string("player", target.name)
                    }
                }
            }
        }
        return@execute 0
    }

    val game = amongUsPlayer.game
    if (game.phase != GamePhase.LOBBY) {
        sendMessage {
            translatable("command.error.set_color.game_not_lobby")
        }
        return@execute 0
    }
    val color = optionalArg<PlayerColor>("color") ?: game.randomPlayerColor()
    val force = optionalArg<Boolean>("force") == true

    val uses = game.players.find { it.color == color }

    if (uses != null && !force) {
        sendMessage {
            translatable("command.error.set_color.color_already_used") {
                args {
                    component("color", color.coloredName)
                }
            }
        }
        return@execute 0
    }

    amongUsPlayer.color = color
    uses?.color = game.randomPlayerColor()

    sendMessage {
        if (uses == null) {
            translatable(if (target === source.sender) "command.success.set_color" else "command.success.set_color.other") {
                args {
                    component("color", color.coloredName)
                    string("player", target.name)
                }
            }
        } else {
            translatable(if (target === source.sender) "command.success.set_color.overridden" else "command.success.set_color.other.overridden") {
                args {
                    string("player", target.name)
                    component("color", color.coloredName)
                    string("other", uses.name)
                    component("other_color", uses.color.coloredName)
                }
            }
        }
    }

    SINGLE_SUCCESS
}

private fun PaperCommand.statsCommand() {
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