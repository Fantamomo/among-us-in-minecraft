package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.command.arguments.GameAreaArgumentType
import com.fantamomo.mc.amongus.command.arguments.GameArgumentType
import com.fantamomo.mc.amongus.command.arguments.TaskIdArgumentType
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.World
import org.bukkit.entity.Player


fun PaperCommand.gameCommand() = literal("game") {
    createGameCommand()
    joinGameCommand()
    listGameCommand()
    startGameCommand()
    taskGameCommand()
    letWinGameCommand()
    killPlayerGameCommand()
}

private fun PaperCommand.killPlayerGameCommand() = literal("kill") {
    argument("target", ArgumentTypes.player()) {
        argument("corpse", BoolArgumentType.bool()) {
            killPlayerGamCommandExecute()
        }
        killPlayerGamCommandExecute()
    }
}

private fun KtCommandBuilder<CommandSourceStack, *>.killPlayerGamCommandExecute() = execute {
    val targetResolver: PlayerSelectorArgumentResolver =
        arg<PlayerSelectorArgumentResolver>("target")
    val target = targetResolver.resolve(source).first()

    val corpse = optionalArg<Boolean>("corpse") == true

    val amongUsPlayer = PlayerManager.getPlayer(target)

    if (amongUsPlayer == null) {
        sendMessage {
            translatable("command.error.admin.game.letwin.not_joined")
        }
        return@execute 0
    }

    val game = amongUsPlayer.game

    when (game.phase) {
        GamePhase.LOBBY, GamePhase.STARTING -> {
            sendMessage {
                translatable("command.error.admin.game.kill.not_started") {
                    args {
                        string("game", game.code)
                    }
                }
            }
            return@execute 0
        }

        GamePhase.FINISHED -> {
            sendMessage {
                translatable("command.error.admin.game.kill.already_finished") {
                    args {
                        string("game", game.code)
                    }
                }
            }
            return@execute 0
        }

        else -> {}
    }

    if (!amongUsPlayer.isAlive) {
        sendMessage {
            translatable("command.error.admin.game.kill.not_alive") {
                args {
                    string("player", target.name)
                }
            }
        }
        return@execute 0
    }

    game.killManager.kill(amongUsPlayer, corpse)

    val loc = target.location
    sendMessage {
        if (corpse) {
            translatable("command.success.admin.game.kill.corpse") {
                args {
                    string("player", target.name)
                    numeric("x", loc.blockX)
                    numeric("y", loc.blockY)
                    numeric("z", loc.blockZ)
                }
            }
        } else {
            translatable("command.success.admin.game.kill") {
                args {
                    string("player", target.name)
                }
            }
        }
    }

    SINGLE_SUCCESS
}

private fun PaperCommand.letWinGameCommand() = literal("letwin") {
    Team.entries.forEach(::letWinGameCommandArgument)
}

private fun PaperCommand.letWinGameCommandArgument(team: Team) = literal(team.name.lowercase()) {
    argument("game", GameArgumentType(false)) {
        letWinGameCommandExecute(team)
    }
    letWinGameCommandExecute(team)
}

private fun KtCommandBuilder<CommandSourceStack, *>.letWinGameCommandExecute(team: Team) = execute {
    var game = optionalArg<Game>("game")

    if (game == null) {
        val execute = source.executor as? Player
        if (execute == null) {
            sendMessage {
                translatable("command.error.admin.game.letwin.not_a_player")
            }
            return@execute 0
        }
        val amongUsPlayer = PlayerManager.getPlayer(execute.uniqueId)
        if (amongUsPlayer == null) {
            sendMessage {
                translatable("command.error.admin.game.letwin.not_joined")
            }
            return@execute 0
        }
        game = amongUsPlayer.game
    }

    when (game.phase) {
        GamePhase.LOBBY, GamePhase.STARTING -> {
            sendMessage {
                translatable("command.error.admin.game.letwin.not_started") {
                    args {
                        string("game", game.code)
                    }
                }
            }
            return@execute 0
        }

        GamePhase.FINISHED -> {
            sendMessage {
                translatable("command.error.admin.game.letwin.already_finished") {
                    args {
                        string("game", game.code)
                    }
                }
            }
            return@execute 0
        }

        else -> {}
    }

    game.letWin(team)

    sendMessage {
        translatable("command.success.admin.game.letwin") {
            args {
                string("team", team.name)
            }
        }
    }

    SINGLE_SUCCESS
}

private fun PaperCommand.taskGameCommand() = literal("task") {
    argument("players", ArgumentTypes.players()) {
        literal("assign") {
            literalExecute("all") {
                val targetResolver: PlayerSelectorArgumentResolver =
                    arg<PlayerSelectorArgumentResolver>("players")
                val targets = targetResolver.resolve(source)

                if (targets.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.task.no_targets")
                    }
                    return@literalExecute 0
                }

                var success = 0
                for (target in targets) {
                    val auPlayer = PlayerManager.getPlayer(target) ?: continue
                    for (task in Task.tasks) {
                        auPlayer.game.taskManager.assignTask(auPlayer, task)
                        success++
                    }
                }

                if (success == 0) {
                    sendMessage {
                        translatable("command.error.admin.game.task.assign.all.failed") {
                            args {
                                numeric("targets", targets.size)
                            }
                        }
                    }
                    return@literalExecute 0
                }

                sendMessage {
                    translatable("command.success.admin.game.task.assign.all") {
                        args {
                            numeric("targets", success)
                        }
                    }
                }
                SINGLE_SUCCESS
            }
            argument("task", TaskIdArgumentType) {
                val taskRef = argRef()
                execute {
                    val targetResolver: PlayerSelectorArgumentResolver =
                        arg<PlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(source)
                    val task = taskRef.get()

                    if (targets.isEmpty()) {
                        sendMessage {
                            translatable("command.error.admin.game.task.no_targets")
                        }
                        return@execute 0
                    }

                    var success = 0
                    for (target in targets) {
                        val auPlayer = PlayerManager.getPlayer(target) ?: continue
                        auPlayer.game.taskManager.assignTask(auPlayer, task)
                        success++
                    }

                    if (success == 0) {
                        sendMessage {
                            translatable("command.error.admin.game.task.assign.failed") {
                                args {
                                    string("task", task.id)
                                    numeric("targets", targets.size)
                                }
                            }
                        }
                        return@execute 0
                    }

                    if (targets.size == 1) {
                        sendMessage {
                            translatable("command.success.admin.game.task.assign") {
                                args {
                                    string("task", task.id)
                                    string("player", targets.first().name)
                                }
                            }
                        }
                    } else {
                        sendMessage {
                            translatable("command.success.admin.game.task.assign.multiple") {
                                args {
                                    string("task", task.id)
                                    numeric("targets", success)
                                }
                            }
                        }
                    }
                    SINGLE_SUCCESS
                }
            }
        }
        literal("unassign") {
            literalExecute("all") {
                val targetResolver: PlayerSelectorArgumentResolver =
                    arg<PlayerSelectorArgumentResolver>("players")
                val targets = targetResolver.resolve(source)

                if (targets.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.task.no_targets")
                    }
                    return@literalExecute 0
                }

                var success = 0
                for (target in targets) {
                    val auPlayer = PlayerManager.getPlayer(target) ?: continue
                    val tasks = auPlayer.tasks.toList()
                    for (task in tasks) {
                        auPlayer.game.taskManager.unassignTask(auPlayer, task.task.task)
                        success++
                    }
                }

                if (success == 0) {
                    sendMessage {
                        translatable("command.error.admin.game.task.unassign.all.failed") {
                            args {
                                numeric("targets", targets.size)
                            }
                        }
                    }
                    return@literalExecute 0
                }

                sendMessage {
                    translatable("command.success.admin.game.task.unassign.all") {
                        args {
                            numeric("targets", success)
                        }
                    }
                }
                SINGLE_SUCCESS
            }
            argument("task", TaskIdArgumentType) {
                val taskRef = argRef()

                execute {
                    val targetResolver: PlayerSelectorArgumentResolver =
                        arg<PlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(source)
                    val task = taskRef.get()

                    if (targets.isEmpty()) {
                        sendMessage {
                            translatable("command.error.admin.game.task.no_targets")
                        }
                        return@execute 0
                    }

                    var success = 0
                    for (target in targets) {
                        val auPlayer = PlayerManager.getPlayer(target) ?: continue
                        auPlayer.game.taskManager.unassignTask(auPlayer, task)
                        success++
                    }

                    if (success == 0) {
                        sendMessage {
                            translatable("command.error.admin.game.task.unassign.failed") {
                                args {
                                    string("task", task.id)
                                    numeric("targets", targets.size)
                                }
                            }
                        }
                        return@execute 0
                    }

                    sendMessage {
                        translatable("command.success.admin.game.task.unassign") {
                            args {
                                string("task", task.id)
                                numeric("target", success)
                            }
                        }
                    }
                    SINGLE_SUCCESS
                }
                suggests {
                    val targetResolver: PlayerSelectorArgumentResolver =
                        context.arg<PlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(context.source)

                    val input = builder.remaining

                    targets
                        .asSequence()
                        .mapNotNull { PlayerManager.getPlayer(it) }
                        .flatMap { it.tasks }
                        .map { it.task.task.id }
                        .filter { it.startsWith(input, ignoreCase = true) }
                        .toSet()
                        .forEach(::suggest)
                }
            }
        }
        literal("complete") {
            literalExecute("all") {
                val targetResolver: PlayerSelectorArgumentResolver =
                    arg<PlayerSelectorArgumentResolver>("players")
                val targets = targetResolver.resolve(source)

                if (targets.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.task.no_targets")
                    }
                    return@literalExecute 0
                }

                var success = 0
                for (target in targets) {
                    val auPlayer = PlayerManager.getPlayer(target) ?: continue
                    auPlayer.tasks.forEach {
                        if (it.completed) return@forEach
                        auPlayer.game.taskManager.completeTask(it.task, modifyStatistics = false)
                        success++
                    }
                }

                if (success == 0) {
                    sendMessage {
                        translatable("command.error.admin.game.task.complete.all.failed") {
                            args {
                                numeric("targets", targets.size)
                            }
                        }
                    }
                    return@literalExecute 0
                }

                sendMessage {
                    translatable("command.success.admin.game.task.complete.all")
                }
                SINGLE_SUCCESS
            }
            argument("task", TaskIdArgumentType) {
                val taskRef = argRef()
                execute {
                    val targetResolver: PlayerSelectorArgumentResolver =
                        arg<PlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(source)
                    val task = taskRef.get()

                    var success = 0
                    for (target in targets) {
                        val auPlayer = PlayerManager.getPlayer(target) ?: continue
                        auPlayer.tasks.forEach {
                            if (it.task.task != task) return@forEach
                            auPlayer.game.taskManager.completeTask(it.task, modifyStatistics = false)
                        }
                        success++
                    }

                    if (success == 0) {
                        sendMessage {
                            translatable("command.error.admin.game.task.complete.failed") {
                                args {
                                    string("task", task.id)
                                    numeric("targets", targets.size)
                                }
                            }
                        }
                        return@execute 0
                    }

                    sendMessage {
                        translatable("command.success.admin.game.task.complete") {
                            args {
                                string("task", task.id)
                                numeric("targets", success)
                            }
                        }
                    }
                    SINGLE_SUCCESS
                }
                suggests {
                    val targetResolver: PlayerSelectorArgumentResolver =
                        context.arg<PlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(context.source)

                    val input = builder.remaining

                    targets
                        .asSequence()
                        .mapNotNull { PlayerManager.getPlayer(it) }
                        .flatMap { it.tasks }
                        .filter { !it.completed }
                        .map { it.task.task.id }
                        .filter { it.startsWith(input, ignoreCase = true) }
                        .toSet()
                        .forEach(::suggest)
                }
            }
        }
    }
}

private fun PaperCommand.startGameCommand() = literal("start") {
    argument("game", GameArgumentType.INSTANCE) {
        requires { executor is Player }
        startGameCommandExecute()
    }
    startGameCommandExecute()
}

private fun KtCommandBuilder<CommandSourceStack, *>.startGameCommandExecute() = execute {
    var game = optionalArg<Game>("game")
    val sender = source.sender

    if (game == null) {
        val execute = source.executor as? Player
        if (execute == null) {
            sendMessage {
                translatable("command.error.admin.game.start.not_a_player")
            }
            return@execute 0
        }
        val amongUsPlayer = PlayerManager.getPlayer(execute.uniqueId)
        if (amongUsPlayer == null) {
            sendMessage {
                if (sender == execute) {
                    translatable("command.error.admin.game.start.not_joined")
                } else {
                    translatable("command.error.admin.game.start.not_joined_other") {
                        args {
                            string("player", execute.name)
                        }
                    }
                }
            }
            return@execute 0
        }
        game = amongUsPlayer.game
    }
    if (game.phase != GamePhase.LOBBY) {
        sendMessage {
            translatable("command.error.admin.game.start.already_started") {
                args {
                    string("game", game.code)
                }
            }
        }
        return@execute 0
    }
    game.start()
    sendMessage {
        translatable("command.success.admin.game.start")
    }
    SINGLE_SUCCESS
}

private fun PaperCommand.listGameCommand() = literal("list") {
    execute {
        val games = GameManager.getGames()
        if (games.isEmpty()) {
            sendMessage {
                translatable("command.error.admin.game.list.no_games")
            }
            return@execute 0
        }
        sendMessage {
            translatable("command.success.admin.game.list") {
                args {
                    numeric("count", games.size)
                }
            }
        }
        for (game in games) {
            sendMessage {
                translatable("command.success.admin.game.list.game") {
                    args {
                        string("code", game.code)
                        string("area", game.area.name)
                        string("phase", game.phase.name.lowercase().replaceFirstChar(Char::uppercase))
                        numeric("players", game.players.size)
                        numeric("max_players", game.maxPlayers)
                    }
                }
            }
        }
        SINGLE_SUCCESS
    }
}

private fun PaperCommand.joinGameCommand() = literal("join") {
    requires { executor is Player }
    argument("game", GameArgumentType.INSTANCE) {
        val gameRef = argRef()
        argument("targets", ArgumentTypes.players()) {
            val targetRef = argRef()

            execute {
                val game = gameRef.get()
                val target = targetRef.get().resolve(source)

                if (target.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.join.many.no_targets")
                    }
                    return@execute NO_SUCCESS
                }

                var success = 0

                for (player in target) {
                    if (game.addPlayer(player)) {
                        success++
                    }
                }

                if (success == 0) {
                    sendMessage {
                        translatable("command.error.admin.game.join.many.failed") {
                            args {
                                string("game", game.code)
                            }
                        }
                    }
                    return@execute NO_SUCCESS
                }
                if (success == target.size) {
                    sendMessage {
                        translatable("command.success.admin.game.join.many.all")
                    }
                    return@execute SINGLE_SUCCESS
                }
                sendMessage {
                    translatable("command.success.admin.game.join.many") {
                        args {
                            numeric("players", success)
                        }
                    }
                }

                SINGLE_SUCCESS
            }
        }
        execute {
            val sender = source.sender
            val executor = source.executor as? Player
            if (executor == null) {
                sendMessage {
                    translatable("command.error.admin.game.join.not_a_player")
                }
                return@execute 0
            }
            val game = gameRef.get()

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