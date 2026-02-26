package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.command.Permissions.required
import com.fantamomo.mc.amongus.command.arguments.*
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerColor
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.PlayerStatistics
import com.fantamomo.mc.amongus.settings.SettingsInventory
import com.fantamomo.mc.amongus.statistics.*
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.BoolArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.registry.RegistryKey
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.trim.ArmorTrim
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.toKotlinUuid

val AmongUsCommand = paperCommand("amongus") {
    statsCommand()
    colorCommand()
    trimCommand()
    joinCommand()
    leaveCommand()
    createCommand()
    settingsCommand()
    startCommand()
    banCommand()
}

private fun PaperCommand.banCommand() = literal("ban") {
    requires {
        sender is Player &&
                (sender.hasPermission(Permissions.ADMIN) ||
                        (AmongUsConfig.GameCreation.everyoneCanCreate && sender.hasPermission(Permissions.PLAYER_BAN)))
    }
    argument("player", AmongUsPlayerArgumentType.SINGLE) {
        val targetRef = argRef()
        execute {
            val sender = source.sender as Player

            val auPlayer = PlayerManager.getPlayer(sender)
            if (auPlayer == null) {
                sendMessage {
                    translatable("command.error.ban.not_joined")
                }
                return@execute NO_SUCCESS
            }

            if (!auPlayer.isHost()) {
                sendMessage {
                    translatable("command.error.ban.not_host")
                }
                return@execute NO_SUCCESS
            }

            val game = auPlayer.game
            if (game.phase != GamePhase.LOBBY) {
                sendMessage {
                    translatable("command.error.ban.not_in_lobby")
                }
                return@execute NO_SUCCESS
            }

            val target = targetRef.get().resolve(source).first()

            if (target === auPlayer) {
                sendMessage {
                    translatable("command.error.ban.self")
                }
                return@execute NO_SUCCESS
            }

            if (game !== target.game) {
                sendMessage {
                    translatable("command.error.ban.not_in_same_game")
                }
                return@execute NO_SUCCESS
            }

            game.bannedPlayers.add(target.uuid)

            PlayerManager.leaveGame(target)

            sendMessage {
                translatable("command.success.ban") {
                    args {
                        string("player", target.name)
                    }
                }
            }

            SINGLE_SUCCESS
        }
    }
}

private fun PaperCommand.startCommand() = literal("start") {
    requires {
        sender is Player &&
                (sender.hasPermission(Permissions.ADMIN_GAME_START) ||
                        (AmongUsConfig.GameCreation.everyoneCanCreate && sender.hasPermission(Permissions.PLAYER_START)))
    }
    execute {
        val sender = source.sender as Player

        val auPlayer = PlayerManager.getPlayer(sender)
        if (auPlayer == null) {
            sendMessage {
                translatable("command.error.start.not_joined")
            }
            return@execute NO_SUCCESS
        }

        if (!auPlayer.isHost()) {
            sendMessage {
                translatable("command.error.start.not_host")
            }
            return@execute NO_SUCCESS
        }

        if (auPlayer.game.phase != GamePhase.LOBBY) {
            sendMessage {
                translatable("command.error.start.already_started") {
                    args {
                        string("game", auPlayer.game.code)
                    }
                }
            }
            return@execute NO_SUCCESS
        }

        auPlayer.game.startStartCooldown()

        SINGLE_SUCCESS
    }
}

private fun PaperCommand.settingsCommand() = literal("settings") {
    requires {
        sender is Player &&
                (sender.hasPermission(Permissions.ADMIN_GAME_CREATE) ||
                        (AmongUsConfig.GameCreation.everyoneCanCreate && sender.hasPermission(Permissions.PLAYER_SETTINGS)))
    }

    execute {
        val sender = source.sender as Player

        val auPlayer = PlayerManager.getPlayer(sender)

        if (auPlayer == null) {
            sendMessage {
                translatable("command.error.admin.settings.not_joined")
            }
            return@execute NO_SUCCESS
        }

        if (!auPlayer.isHost()) {
            sendMessage {
                translatable("command.error.settings.not_host")
            }
            return@execute NO_SUCCESS
        }

        val settingsInventory = SettingsInventory(auPlayer)
        sender.openInventory(settingsInventory.inventory)

        SINGLE_SUCCESS
    }
}

private fun PaperCommand.createCommand() = literal("create") {
    requires {
        sender is Player &&
                (sender.hasPermission(Permissions.ADMIN_GAME_CREATE) ||
                        (sender.hasPermission(Permissions.PLAYER_CREATE) && AmongUsConfig.GameCreation.everyoneCanCreate))
    }

    argument("area", GameAreaArgumentType) {
        execute {
            val player = source.sender as Player
            if (PlayerManager.getPlayer(player) != null) {
                sendMessage {
                    translatable("command.error.game.create.in_game")
                }
                return@execute 0
            }

            if (GameManager.getGames().size >= AmongUsConfig.GameCreation.maxGames && (!AmongUsConfig.GameCreation.ignoreAdmins || !player.hasPermission(
                    Permissions.ADMIN_GAME_CREATE
                ))
            ) {
                sendMessage {
                    translatable("command.error.game.create.max_reached") {
                        args {
                            numeric("max", AmongUsConfig.GameCreation.maxGames)
                        }
                    }
                }
                return@execute 0
            }

            val area = arg<GameArea>("area")

            val missedLocations = area.getMissedLocations()
            if (missedLocations.isNotEmpty()) {
                sendMessage {
                    translatable("command.error.admin.game.create.failed")
                }
                return@execute 0
            }

            val maxPlayers = Game.DEFAULT_MAX_PLAYERS

            sendMessage {
                translatable("command.success.admin.game.create.creating") {
                    args {
                        string("area", area.name)
                    }
                }
            }

            GameManager.createGame(area, maxPlayers) { game ->
                if (game != null) {
                    sendMessage {
                        translatable("command.success.admin.game.create") {
                            args {
                                string("area", area.name)
                                numeric("max_players", maxPlayers)
                                string("world", game.world.name.replace('\\', '/').substringAfterLast('/'))
                                string("code", game.code)
                            }
                        }
                    }
                    if (game.addPlayer(player)) {
                        val auPlayer = PlayerManager.getPlayer(player)!!
                        game.host = auPlayer
                    }
                } else {
                    sendMessage {
                        translatable("command.error.admin.game.create.failed")
                    }
                }
            }

            return@execute SINGLE_SUCCESS
        }
    }
}

private fun PaperCommand.leaveCommand() = literal("leave") {
    Permissions.PLAYER_LEAVE.required()
    execute {
        val executor = source.executor as? Player
        if (executor == null) {
            sendMessage {
                translatable("command.error.leave.not_player")
            }
            return@execute 0
        }
        val amongUsPlayer = PlayerManager.getPlayer(executor)
        if (amongUsPlayer == null) {
            sendMessage {
                translatable("command.error.leave.not_joined")
            }
            return@execute 0
        }
        val game = amongUsPlayer.game
        if (game.phase != GamePhase.LOBBY) {
            sendMessage {
                translatable("command.error.leave.not_in_lobby")
            }
            return@execute 0
        }

        PlayerManager.leaveGame(amongUsPlayer)

        sendMessage {
            translatable("command.success.leave")
        }

        SINGLE_SUCCESS
    }
}

private fun PaperCommand.joinCommand() = literal("join") {
    Permissions.PLAYER_JOIN.required()
    argument("game", GameArgumentType.INSTANCE) {
        val gameRef = argRef()
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
            if (executor.uniqueId in game.bannedPlayers) {
                sendMessage {
                    translatable("command.error.admin.game.join.banned")
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

private fun PaperCommand.trimCommand() = literal("trim") {
    Permissions.SET_PLAYER_TRIM.required()

    guard {
        val targetResolver = optionalArg<PlayerSelectorArgumentResolver>("target")

        val target = targetResolver?.resolve(source)?.firstOrNull() ?: source.sender as? Player

        if (target == null) {
            sendMessage {
                translatable("command.error.set_trim.not_player")
            }
            return@guard abort()
        }
        val amongUsPlayer = PlayerManager.getPlayer(target)
        if (amongUsPlayer == null) {
            sendMessage {
                if (target === source.sender) translatable("command.error.set_trim.not_joined")
                else translatable("command.error.set_trim.not_joined.other") {
                    args {
                        string("player", target.name)
                    }
                }
            }
            return@guard abort()
        }

        if (amongUsPlayer.game.phase != GamePhase.LOBBY && amongUsPlayer.game.phase != GamePhase.STARTING) {
            sendMessage {
                translatable("command.error.set_trim.game_not_lobby")
            }
            return@guard abort()
        }

        continueCommand()
    }

    argument("material", RegistryArgumentType(RegistryKey.TRIM_MATERIAL)) {
        val materialArg = argRef()
        argument("pattern", RegistryArgumentType(RegistryKey.TRIM_PATTERN)) {
            val patternArg = argRef()

            argument("target", ArgumentTypes.player()) {
                requires { sender.hasPermission(Permissions.SET_PLAYER_TRIM_OTHER) }
                execute {
                    val material = materialArg.get()
                    val pattern = patternArg.get()
                    val targetResolver = arg<PlayerSelectorArgumentResolver>("target")
                    val targets = targetResolver.resolve(source).first()
                    val amongUsPlayer = PlayerManager.getPlayer(targets)!!
                    amongUsPlayer.armorTrim = ArmorTrim(material, pattern)

                    sendMessage {
                        translatable("command.success.set_trim.other") {
                            args {
                                component("material", material.description())
                                component("pattern", pattern.description())
                                string("player", targets.name)
                            }
                        }
                    }

                    SINGLE_SUCCESS
                }
            }

            execute {
                val material = materialArg.get()
                val pattern = patternArg.get()
                val amongUsPlayer = PlayerManager.getPlayer(source.sender as Player)!!
                amongUsPlayer.armorTrim = ArmorTrim(material, pattern)

                sendMessage {
                    translatable("command.success.set_trim") {
                        args {
                            component("material", material.description())
                            component("pattern", pattern.description())
                        }
                    }
                }

                SINGLE_SUCCESS
            }
        }
    }

    literal("remove") {
        argument("target", ArgumentTypes.player()) {
            requires { sender.hasPermission(Permissions.SET_PLAYER_TRIM_OTHER) }
            execute {
                val targetResolver = arg<PlayerSelectorArgumentResolver>("target")
                val target = targetResolver.resolve(source).first()
                val amongUsPlayer = PlayerManager.getPlayer(target)!!
                amongUsPlayer.armorTrim = null
                sendMessage {
                    translatable("command.success.remove_trim.other") {
                        args {
                            string("player", target.name)
                        }
                    }
                }
                SINGLE_SUCCESS
            }
        }
        execute {
            val amongUsPlayer = PlayerManager.getPlayer(source.sender as Player)!!
            amongUsPlayer.armorTrim = null
            sendMessage {
                translatable("command.success.remove_trim")
            }
            SINGLE_SUCCESS
        }
    }
}

private fun PaperCommand.colorCommand() = literal("color") {
    Permissions.SET_PLAYER_COLOR.required()
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
    if (game.phase != GamePhase.LOBBY && game.phase != GamePhase.STARTING) {
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

private fun PaperCommand.statsCommand() = literal("stats") {
    Permissions.PLAYER_STATS.required()
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