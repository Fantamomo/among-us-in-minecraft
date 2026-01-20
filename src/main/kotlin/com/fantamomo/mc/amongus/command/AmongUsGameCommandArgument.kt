package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.command.arguments.GameAreaArgumentType
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.World
import org.bukkit.entity.Player

fun PaperCommand.gameCommand() = literal("game") {
    createGameCommand()
    joinGameCommand()
}

private fun PaperCommand.joinGameCommand() = literal("join") {
    requires { executor is Player }
    argument("game", GameAreaArgumentType) {
        execute {
            val sender = source.sender
            val executor = source.executor as Player
            val game = arg<Game>("game")

            if (game.phase != GamePhase.LOBBY) {
                sendMessage {
                    translatable("command.error.admin.game.join.already_started")
                }
                return@execute 0
            }
            if (game.players.size >= game.maxPlayers) {
                sendMessage {
                    translatable("command.error.admin.game.join.full")
                }
                return@execute 0
            }
            if (PlayerManager.exists(executor.uniqueId)) {
                sendMessage {
                    if (sender == executor) {
                        translatable("command.error.admin.game.join.already_joined")
                    } else {
                        translatable("command.error.admin.game.join.already_joined_other") {
                            args {
                                string("player", executor.name)
                            }
                        }
                    }
                }
                return@execute 0
            }
            val success = game.addPlayer(executor)
            if (!success) {
                sendMessage {
                    translatable("command.error.admin.game.join.unknown")
                }
                return@execute 0
            }
            sendMessage {
                if (sender == executor) {
                    translatable("command.success.admin.game.join")
                } else {
                    translatable("command.success.admin.game.join_other") {
                        args {
                            string("player", executor.name)
                        }
                    }
                }
            }

            SINGLE_SUCCESS
        }
    }
}

private fun PaperCommand.createGameCommand() = literal("create") {
    argument("area", GameAreaArgumentType) {
        argument("world", ArgumentTypes.world()) {
            argument("maxPlayers", IntegerArgumentType.integer(1, 16)) {
                createGameCommandExecute()
            }
            createGameCommandExecute()
        }
        createGameCommandExecute()
    }
}

private fun KtArgumentCommandBuilder<CommandSourceStack, *>.createGameCommandExecute() = execute {
    val area = arg<GameArea>("area")

    val world = optionalArg<World>("world") ?: source.location.world

    val maxPlayers = optionalArg<Int>("maxPlayers") ?: Game.DEFAULT_MAX_PLAYERS

    val game = Game(area, world, maxPlayers)

    GameManager.addGame(game)

    sendMessage {
        translatable("command.success.admin.game.create") {
            args {
                string("area", area.name)
                numeric("max_players", maxPlayers)
                string("world", world.name)
                string("code", game.code)
            }
        }
    }

    SINGLE_SUCCESS
}

