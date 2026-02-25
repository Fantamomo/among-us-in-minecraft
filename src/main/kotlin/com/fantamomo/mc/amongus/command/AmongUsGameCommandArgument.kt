package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.command.Permissions.required
import com.fantamomo.mc.amongus.command.arguments.*
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.*


fun PaperCommand.gameCommand() = literal("game") {
    createGameCommand()
    joinGameCommand()
    listGameCommand()
    startGameCommand()
    taskGameCommand()
    letWinGameCommand()
    killPlayerGameCommand()
    roleGameCommand()
    playerInfoGameCommand()
}

private fun PaperCommand.playerInfoGameCommand() = literal("info") {
    Permissions.ADMIN_GAME_INFO.required()
    argument("target", AmongUsPlayerArgumentType.SINGLE) {
        execute {
            val targetResolver = arg<AmongUsPlayerSelectorArgumentResolver>("target")
            val target: AmongUsPlayer = targetResolver.resolve(source).first()

            val role = target.assignedRole
            val tasks = target.tasks.toList()
            val completedTasks = tasks.count { it.completed }
            val location = target.livingEntityOrNull?.location

            sendMessage {
                translatable("command.success.admin.game.info.header") {
                    args {
                        string("player", target.name)
                        string("uuid", target.uuid.toString())
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.identity")
            }
            sendMessage {
                translatable("command.success.admin.game.info.locale") {
                    args {
                        string("locale", target.locale.toLanguageTag())
                        string("language", target.locale.getDisplayLanguage(Locale.US))
                    }
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.color") {
                    args {
                        string("color", target.color.name)
                        string("visible_color", target.visibleColor.name)
                        component("morphed") {
                            translatable(
                                if (target.game.morphManager.isMorphed(target)) "command.success.admin.game.info.yes"
                                else "command.success.admin.game.info.no"
                            )
                        }
                    }
                }
            }
            target.armorTrim?.let { trim ->
                sendMessage {
                    translatable("command.success.admin.game.info.armor_trim") {
                        args {
                            component("material", trim.material.description())
                            component("pattern", trim.pattern.description())
                        }
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.game")
            }
            sendMessage {
                translatable("command.success.admin.game.info.game") {
                    args {
                        string("code", target.game.code)
                        string("area", target.game.area.name)
                        string("phase", target.game.phase.name.lowercase().replaceFirstChar(Char::uppercase))
                        numeric("players", target.game.players.size)
                        numeric("max_players", target.game.maxPlayers)
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.status")
            }
            sendMessage {
                translatable("command.success.admin.game.info.alive") {
                    args {
                        component("value") {
                            translatable(if (target.isAlive) "command.success.admin.game.info.alive.yes" else "command.success.admin.game.info.alive.no")
                        }
                    }
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.online") {
                    args {
                        component("value") {
                            translatable(if (target.player != null) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                        }
                    }
                }
            }
            target.disconnectedAt?.let {
                sendMessage {
                    translatable("command.success.admin.game.info.disconnected_at") {
                        args {
                            string("time", it.toString())
                        }
                    }
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.ghost_form") {
                    args {
                        component("value") {
                            translatable(if (target.isInGhostForm()) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                        }
                    }
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.meetings_pressed") {
                    args {
                        numeric("count", target.meetingButtonsPressed)
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.location")
            }
            if (location != null) {
                sendMessage {
                    translatable("command.success.admin.game.info.location") {
                        args {
                            numeric("x", location.blockX)
                            numeric("y", location.blockY)
                            numeric("z", location.blockZ)
                            string("world", location.world.name)
                        }
                    }
                }
                sendMessage {
                    translatable("command.success.admin.game.info.location.entity_type") {
                        args {
                            string(
                                "type", when {
                                    target.player != null -> "Player"
                                    target.mannequinController.getEntity() != null -> "Mannequin"
                                    else -> "None"
                                }
                            )
                        }
                    }
                }
            } else {
                sendMessage {
                    translatable("command.success.admin.game.info.location.none")
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.vented") {
                    args {
                        component("value") {
                            translatable(if (target.isVented()) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                        }
                    }
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.near_vent") {
                    args {
                        component("value") {
                            translatable(if (target.isNearVent()) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                        }
                    }
                }
            }
            sendMessage {
                translatable("command.success.admin.game.info.in_cams") {
                    args {
                        component("value") {
                            translatable(if (target.isInCams()) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                        }
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.role")
            }
            if (role != null) {
                sendMessage {
                    translatable("command.success.admin.game.info.role") {
                        args {
                            component("role", role.name)
                            string("team", role.definition.team.name)
                        }
                    }
                }
                sendMessage {
                    translatable("command.success.admin.game.info.role.can_do_tasks") {
                        args {
                            component("value") {
                                translatable(if (target.canDoTasks) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                            }
                        }
                    }
                }
                sendMessage {
                    translatable("command.success.admin.game.info.role.can_see_lights") {
                        args {
                            component("value") {
                                translatable(if (target.canSeeWhenLightsSabotage()) "command.success.admin.game.info.yes" else "command.success.admin.game.info.no")
                            }
                        }
                    }
                }
            } else {
                sendMessage {
                    translatable("command.success.admin.game.info.role.none")
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.abilities") {
                    args {
                        numeric("count", target.abilities.size)
                    }
                }
            }
            if (target.abilities.isEmpty()) {
                sendMessage {
                    translatable("command.success.admin.game.info.abilities.none")
                }
            } else {
                for (ability in target.abilities) {
                    sendMessage {
                        translatable("command.success.admin.game.info.ability.entry") {
                            args {
                                string("ability", ability.definition.id)
                                numeric("items", ability.items.size)
                            }
                        }
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.section.tasks") {
                    args {
                        numeric("completed", completedTasks)
                        numeric("total", tasks.size)
                    }
                }
            }
            if (tasks.isEmpty()) {
                sendMessage {
                    translatable("command.success.admin.game.info.tasks.none")
                }
            } else {
                for (registeredTask in tasks) {
                    sendMessage {
                        translatable(
                            if (registeredTask.completed)
                                "command.success.admin.game.info.task.entry.completed"
                            else
                                "command.success.admin.game.info.task.entry.pending"
                        ) {
                            args {
                                string("task", registeredTask.task.task.id)
                            }
                        }
                    }
                }
            }

            sendMessage {
                translatable("command.success.admin.game.info.footer")
            }

            SINGLE_SUCCESS
        }
    }
}

private fun PaperCommand.roleGameCommand() = literal("role") {
    Permissions.ADMIN_GAME_ROLE.required()
    guard {
        val targetResolver = optionalArg<AmongUsPlayerSelectorArgumentResolver>("target")

        val target = targetResolver?.resolve(source)?.firstOrNull()
            ?: (source.sender as? Player)?.let { PlayerManager.getPlayer(it) }

        if (target == null) {
            sendMessage {
                translatable("command.error.admin.game.role.not_player")
            }
            return@guard abort()
        }

        val game = target.game

        if (game.phase != GamePhase.LOBBY) {
            sendMessage {
                translatable("command.error.admin.game.role.not_in_lobby")
            }
            return@guard abort()
        }

        setArgument("player", target)

        continueCommand()
    }

    roleSubCommand(
        name = "force",
        successKeySelf = "command.success.admin.game.role.force",
        successKeyOther = "command.success.admin.game.role.force.other"
    ) { player, role ->
        player.game.roleManager.forceRole(player, role)
    }

    roleSubCommand(
        name = "block",
        successKeySelf = "command.success.admin.game.role.block",
        successKeyOther = "command.success.admin.game.role.block.other"
    ) { player, role ->
        player.game.roleManager.blockRole(player, role)
    }

    roleSubCommand(
        name = "unblock",
        successKeySelf = "command.success.admin.game.role.unblock",
        successKeyOther = "command.success.admin.game.role.unblock.other"
    ) { player, role ->
        player.game.roleManager.unblockRole(player, role)
    }

    roleSubCommand(
        name = "allow",
        successKeySelf = "command.success.admin.game.role.allow",
        successKeyOther = "command.success.admin.game.role.allow.other"
    ) { player, role ->
        player.game.roleManager.allowRole(player, role)
    }

    literal("team") {
        Team.teams.forEach(::subRoleTeamGameCommand)
        literal("random") {
            argument("target", AmongUsPlayerArgumentType.SINGLE) {
                execute {
                    val amongUsPlayer = arg<AmongUsPlayer>("player")
                    amongUsPlayer.game.roleManager.restrictTeam(amongUsPlayer, null)
                    sendMessage {
                        translatable("command.success.admin.game.role.team.random.other") {
                            args {
                                string("player", amongUsPlayer.name)
                            }
                        }
                    }
                    SINGLE_SUCCESS
                }
            }
            execute {
                val amongUsPlayer = arg<AmongUsPlayer>("player")
                amongUsPlayer.game.roleManager.restrictTeam(amongUsPlayer, null)
                sendMessage {
                    translatable("command.success.admin.game.role.team.random")
                }
                SINGLE_SUCCESS
            }
        }
    }
}

private fun PaperCommand.subRoleTeamGameCommand(team: Team, step: Boolean = true) {
    if (team is Team.NEUTRAL && step) {
        literal("neutral") {
            subRoleTeamGameCommand(team, false)
        }
        return
    }
    literal(team.name) {
        roleTeamGameCommand(team)
    }
}

private fun KtCommandBuilder<CommandSourceStack, *>.roleTeamGameCommand(team: Team) {
    argument("target", AmongUsPlayerArgumentType.SINGLE) {
        execute {
            val amongUsPlayer = arg<AmongUsPlayer>("player")
            amongUsPlayer.game.roleManager.restrictTeam(amongUsPlayer, team)
            sendMessage {
                translatable("command.success.admin.game.role.team.other") {
                    args {
                        string("player", amongUsPlayer.name)
                        string("team", team.name)
                    }
                }
            }
            SINGLE_SUCCESS
        }
    }
    execute {
        val amongUsPlayer = arg<AmongUsPlayer>("player")
        amongUsPlayer.game.roleManager.restrictTeam(amongUsPlayer, team)
        sendMessage {
            translatable("command.success.admin.game.role.team") {
                args {
                    string("team", team.name)
                }
            }
        }
        SINGLE_SUCCESS
    }
}

private fun PaperCommand.roleSubCommand(
    name: String,
    successKeySelf: String,
    successKeyOther: String,
    action: (AmongUsPlayer, Role<*, *>) -> Unit
) = literal(name) {
    argument("role", RoleArgumentType.ALL) {
        fun KtCommandBuilder<CommandSourceStack, *>.executeRole(targeted: Boolean) = execute {
            val amongUsPlayer = arg<AmongUsPlayer>("player")
            val role = arg<Role<*, *>>("role")

            action(amongUsPlayer, role)

            sendMessage {
                translatable(if (targeted) successKeyOther else successKeySelf) {
                    args {
                        if (targeted) {
                            string("player", amongUsPlayer.name)
                        }
                        component("role", role.name)
                    }
                }
            }

            SINGLE_SUCCESS
        }

        argument("target", AmongUsPlayerArgumentType.SINGLE) {
            executeRole(true)
        }

        executeRole(false)
    }
}

private fun PaperCommand.killPlayerGameCommand() = literal("kill") {
    Permissions.ADMIN_GAME_KILL.required()
    argument("target", AmongUsPlayerArgumentType.SINGLE) {
        argument("corpse", BoolArgumentType.bool()) {
            killPlayerGamCommandExecute()
        }
        killPlayerGamCommandExecute()
    }
}

private fun KtCommandBuilder<CommandSourceStack, *>.killPlayerGamCommandExecute() = execute {
    val targetResolver = arg<AmongUsPlayerSelectorArgumentResolver>("target")
    val amongUsPlayer = targetResolver.resolve(source).first()

    val corpse = optionalArg<Boolean>("corpse") == true

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
                    string("player", amongUsPlayer.name)
                }
            }
        }
        return@execute 0
    }

    val loc = (amongUsPlayer.mannequinController.getEntity() ?: amongUsPlayer.livingEntity).location
    sendMessage {
        if (corpse) {
            translatable("command.success.admin.game.kill.corpse") {
                args {
                    string("player", amongUsPlayer.name)
                    numeric("x", loc.blockX)
                    numeric("y", loc.blockY)
                    numeric("z", loc.blockZ)
                }
            }
        } else {
            translatable("command.success.admin.game.kill") {
                args {
                    string("player", amongUsPlayer.name)
                }
            }
        }
    }

    game.killManager.kill(amongUsPlayer, corpse)

    SINGLE_SUCCESS
}

private fun PaperCommand.letWinGameCommand() = literal("letwin") {
    Permissions.ADMIN_GAME_LET_WIN.required()
    Team.teams.forEach(::letWinGameCommandArgument)
}

private fun PaperCommand.letWinGameCommandArgument(team: Team, step: Boolean = false) {
    if (team is Team.NEUTRAL && !step) {
        literal("neutral") {
            letWinGameCommandArgument(team, true)
        }
        return
    }
    literal(team.name) {
        argument("game", GameArgumentType(false)) {
            letWinGameCommandExecute(team)
        }
        letWinGameCommandExecute(team)
    }
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
    Permissions.ADMIN_GAME_TASK.required()
    argument("players", AmongUsPlayerArgumentType.MANY) {
        literal("assign") {
            literalExecute("all") {
                val targetResolver =
                    arg<AmongUsPlayerSelectorArgumentResolver>("players")
                val targets = targetResolver.resolve(source)

                if (targets.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.task.no_targets")
                    }
                    return@literalExecute 0
                }

                var success = 0
                for (auPlayer in targets) {
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
                    val targetResolver =
                        arg<AmongUsPlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(source)
                    val task = taskRef.get()

                    if (targets.isEmpty()) {
                        sendMessage {
                            translatable("command.error.admin.game.task.no_targets")
                        }
                        return@execute 0
                    }

                    var success = 0
                    for (auPlayer in targets) {
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
                val targetResolver =
                    arg<AmongUsPlayerSelectorArgumentResolver>("players")
                val targets = targetResolver.resolve(source)

                if (targets.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.task.no_targets")
                    }
                    return@literalExecute 0
                }

                var success = 0
                for (auPlayer in targets) {
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
                    val targetResolver =
                        arg<AmongUsPlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(source)
                    val task = taskRef.get()

                    if (targets.isEmpty()) {
                        sendMessage {
                            translatable("command.error.admin.game.task.no_targets")
                        }
                        return@execute 0
                    }

                    var success = 0
                    for (auPlayer in targets) {
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
                    val targetResolver =
                        context.arg<AmongUsPlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(context.source)

                    val input = builder.remaining

                    targets
                        .asSequence()
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
                val targetResolver =
                    arg<AmongUsPlayerSelectorArgumentResolver>("players")
                val targets = targetResolver.resolve(source)

                if (targets.isEmpty()) {
                    sendMessage {
                        translatable("command.error.admin.game.task.no_targets")
                    }
                    return@literalExecute 0
                }

                var success = 0
                for (auPlayer in targets) {
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
                    val targetResolver =
                        arg<AmongUsPlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(source)
                    val task = taskRef.get()

                    var success = 0
                    for (auPlayer in targets) {
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
                    val targetResolver =
                        context.arg<AmongUsPlayerSelectorArgumentResolver>("players")
                    val targets = targetResolver.resolve(context.source)

                    val input = builder.remaining

                    targets
                        .asSequence()
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
    Permissions.ADMIN_GAME_START.required()
    argument("game", GameArgumentType.INSTANCE) {
        requires { executor is Player }
        argument("force", BoolArgumentType.bool()) {
            startGameCommandExecute()
        }
        startGameCommandExecute()
    }
    startGameCommandExecute()
}

private fun KtCommandBuilder<CommandSourceStack, *>.startGameCommandExecute() = execute {
    var game = optionalArg<Game>("game")
    val sender = source.sender

    val force = optionalArg<Boolean>("force") ?: false

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

    if (game.players.size < Game.NEEDED_PLAYERS_FOR_START && game.settings[SettingsKey.DEV.DO_WIN_CHECK]) {
        sendMessage {
            translatable("command.error.admin.game.start.not_enough_players") {
                args {
                    string("game", game.code)
                }
            }
        }
        return@execute 0
    }

    if (force) {
        game.start()
        sendMessage {
            translatable("command.success.admin.game.start.force")
        }
    } else {
        game.startStartCooldown()
        sendMessage {
            translatable("command.success.admin.game.start")
        }
    }
    SINGLE_SUCCESS
}

private fun PaperCommand.listGameCommand() = literal("list") {
    Permissions.ADMIN_GAME_LIST.required()
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
    Permissions.ADMIN_GAME_JOIN.required()
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
    Permissions.ADMIN_GAME_CREATE.required()
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

    val missedLocations = area.getMissedLocations()
    if (missedLocations.isNotEmpty()) {
        sendMessage {
            translatable("command.error.admin.game.create.missing_locations") {
                args {
                    component("missing") {
                        var first = true
                        for (missedLocation in missedLocations) {
                            if (first) first = false
                            else text(", ", NamedTextColor.GRAY)
                            translatable("area.location.name.$missedLocation") {
                                hoverEvent(KHoverEventType.ShowText) {
                                    translatable("area.location.description.$missedLocation")
                                }
                            }
                        }
                    }
                }
            }
        }
        return@execute 0
    }

    val maxPlayers = optionalArg<Int>("maxPlayers") ?: Game.DEFAULT_MAX_PLAYERS

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
        } else {
            sendMessage {
                translatable("command.error.admin.game.create.failed")
            }
        }
    }

    return@execute SINGLE_SUCCESS
}