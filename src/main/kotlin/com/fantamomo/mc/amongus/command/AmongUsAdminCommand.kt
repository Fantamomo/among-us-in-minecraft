@file:Suppress("UnstableApiUsage")

package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.ability.abilities.*
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.area.GameAreaManager
import com.fantamomo.mc.amongus.area.VentGroup
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.settings.SettingsInventory
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.settings.SettingsType
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.util.sendComponent
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver
import io.papermc.paper.math.Rotation
import org.bukkit.Location
import org.bukkit.entity.Player

val AmongUsAdminCommand = paperCommand("amongusadmin") {
    Permissions.ADMIN.required()
    areaCommand()
    settingsCommand()
    gameCommand()
}

private fun PaperCommand.settingsCommand() = literal("settings") {
    requires { sender is Player && sender.hasPermission(Permissions.SETTINGS) }

    execute {
        val sender = source.sender as Player

        val auPlayer = PlayerManager.getPlayer(sender)

        if (auPlayer == null) {
            sendMessage {
                translatable("command.error.admin.settings.not_joined")
            }
            return@execute NO_SUCCESS
        }

        val settingsInventory = SettingsInventory(auPlayer)
        sender.openInventory(settingsInventory.inventory)

        SINGLE_SUCCESS
    }

    val keys = SettingsKey.keys()

    for (key in keys) {
        literal(key.key) {
            literalExecute("get") {
                val player = source.sender as Player
                val amongUsPlayer = PlayerManager.getPlayer(player)
                if (amongUsPlayer == null) {
                    sendMessage {
                        translatable("command.error.admin.settings.not_joined")
                    }
                    return@literalExecute 0
                }
                val value = amongUsPlayer.game.settings[key]

                @Suppress("UNCHECKED_CAST")
                val type = key.type as SettingsType<Any>
                source.sender.sendComponent {
                    translatable("command.success.admin.settings.get") {
                        args {
                            component("key") {
                                content(key.key)
                                hoverEvent(KHoverEventType.ShowText) {
                                    append(key.settingsDisplayName)
                                    if (key.settingsDescription != null) {
                                        newLine()
                                        append(key.settingsDescription)
                                    }
                                }
                            }
                            string("default_value", type.stringRepresentation(key.defaultValue))
                            string("value", type.stringRepresentation(value))
                        }
                    }
                }
                SINGLE_SUCCESS
            }
            literal("set") {
                argument("value", key.type.argumentType) {
                    execute {
                        val player = source.sender as Player
                        val amongUsPlayer = PlayerManager.getPlayer(player)
                        if (amongUsPlayer == null) {
                            sendMessage {
                                translatable("command.error.admin.settings.not_joined")
                            }
                            return@execute 0
                        }

                        val value = arg<Any>("value")

                        @Suppress("UNCHECKED_CAST")
                        amongUsPlayer.game.settings.set(key as SettingsKey<Any, *>, value)

                        player.sendComponent {
                            translatable("command.success.admin.settings.set") {
                                args {
                                    component("key") {
                                        content(key.key)
                                        hoverEvent(KHoverEventType.ShowText) {
                                            append(key.settingsDisplayName)
                                            if (key.settingsDescription != null) {
                                                newLine()
                                                append(key.settingsDescription)
                                            }
                                        }
                                    }
                                    string("value", key.type.stringRepresentation(value))
                                }
                            }
                        }

                        SINGLE_SUCCESS
                    }
                }
            }
            literalExecute("reset") {
                val player = source.sender as Player
                val amongUsPlayer = PlayerManager.getPlayer(player)
                if (amongUsPlayer == null) {
                    sendMessage {
                        translatable("command.error.admin.settings.not_joined")
                    }
                    return@literalExecute 0
                }
                amongUsPlayer.game.settings.remove(key)
                player.sendComponent {
                    translatable("command.success.admin.settings.reset") {
                        args {
                            component("key") {
                                content(key.key)
                                hoverEvent(KHoverEventType.ShowText) {
                                    append(key.settingsDisplayName)
                                    if (key.settingsDescription != null) {
                                        newLine()
                                        append(key.settingsDescription)
                                    }
                                }
                            }
                            @Suppress("UNCHECKED_CAST")
                            key as SettingsKey<Any, *>
                            string("value", key.type.stringRepresentation(key.defaultValue))
                        }
                    }
                }
                SINGLE_SUCCESS
            }
        }
    }
}

private fun PaperCommand.testCommand() {
    literal("test") {
        literalExecute("createGame") {
            val game = Game(GameAreaManager.getArea("test")!!, (source.sender as Player).world, 16)
            GameManager.addGame(game)
            SINGLE_SUCCESS
        }
        literalExecute("join") {
            val game = GameManager.getGames().last()
            game.addPlayer(source.sender as Player)
            SINGLE_SUCCESS
        }
        literalExecute("get") {
            val player = source.sender as Player
            val amongUsPlayer = PlayerManager.getPlayer(player)!!

            amongUsPlayer.addNewAbility(KillAbility)
            amongUsPlayer.addNewAbility(VentAbility)
            amongUsPlayer.addNewAbility(SabotageAbility)
            amongUsPlayer.addNewAbility(RemoteCameraAbility)
            amongUsPlayer.addNewAbility(CallMeetingAbility)

            SINGLE_SUCCESS
        }
        literalExecute("assign") {
            val player = source.sender as Player
            val amongUsPlayer = PlayerManager.getPlayer(player)!!

            Task.tasks.forEach {
                amongUsPlayer.game.taskManager.assignTask(amongUsPlayer, it)
            }

            SINGLE_SUCCESS
        }
    }
}

private fun PaperCommand.areaCommand() {
    Permissions.AREA.required()
    literal("area") {
        argument("area", StringArgumentType.word()) {
            val areaRef = argRef()

            suggests {
                for (area in GameAreaManager.getAreas()) {
                    suggest(area.name)
                }
            }

            guard {
                val name = areaRef.get()
                val area = GameAreaManager.getArea(name)

                if (area == null) {
                    source.sender.sendMessage(textComponent {
                        translatable("command.error.admin.area.not_found") {
                            args {
                                string("area", name)
                            }
                        }
                    })
                    return@guard abort(NO_SUCCESS)
                }

                setArgument("area", area)
                continueCommand()
            }

            areaCreateCommand()
            areaSetLocationCommand()
            areaAddLocationCommand()
        }
    }
}

private fun KtArgumentCommandBuilder<CommandSourceStack, String>.areaAddLocationCommand() = literal("add") {
    literal("vents") {
        argument("id", IntegerArgumentType.integer()) {
            argument("location", ArgumentTypes.blockPosition()) {
                execute {
                    val area = arg<GameArea>("area")
                    val id = arg<Int>("id")

                    val positionResolver = arg<BlockPositionResolver>("location")
                    val position = positionResolver.resolve(source)

                    val location = Location(null, position.x(), position.y(), position.z())

                    val ventGroupIndex = area.vents.indexOfFirst { it.id == id }
                    val vents = if (ventGroupIndex == -1) {
                        setOf(location)
                    } else (area.vents[ventGroupIndex].vents + location).toSet()

                    val group = VentGroup(id, vents)
                    if (ventGroupIndex != -1) {
                        area.vents[ventGroupIndex] = group
                    } else area.vents.add(group)

                    SINGLE_SUCCESS
                }
            }
        }
    }
    literal("cams") {
        argument("name", StringArgumentType.string()) {
            literalExecute("@s") {
                val area = arg<GameArea>("area")
                val name = arg<String>("name")

                area.cams[name] = (source.sender as Player).eyeLocation

                SINGLE_SUCCESS
            }
        }
    }
    literal("lights_levers") {
        argument("block", ArgumentTypes.blockPosition()) {
            execute {
                val area = arg<GameArea>("area")

                val positionResolver = arg<BlockPositionResolver>("block")
                val position = positionResolver.resolve(source)

                val location = Location(null, position.x(), position.y(), position.z())

                area.lightLevers.add(location)

                SINGLE_SUCCESS
            }
        }
    }
    literal("tasks") {
        argument("task", StringArgumentType.word()) {
            argument("block", ArgumentTypes.blockPosition()) {
                execute {
                    val area = arg<GameArea>("area")
                    val taskName = arg<String>("task")

                    val positionResolver = arg<BlockPositionResolver>("block")
                    val position = positionResolver.resolve(source)

                    val location = Location(null, position.x(), position.y(), position.z())

                    area.tasks.computeIfAbsent(taskName) { mutableSetOf() }.add(location)

                    SINGLE_SUCCESS
                }
            }
        }
    }
}

private fun KtArgumentCommandBuilder<CommandSourceStack, String>.areaSetLocationCommand() = literal("set") {
    val locationProperties = GameArea.properties
    argument("location_name", StringArgumentType.word()) {
        suggests {
            val remaining = builder.remaining
            for ((name, _) in locationProperties) {
                if (name.startsWith(remaining, ignoreCase = true)) {
                    suggest(name)
                }
            }
        }
        argument("location", ArgumentTypes.finePosition(true)) {
            argument("rotation", ArgumentTypes.rotation()) {
                execute {
                    val area = arg<GameArea>("area")
                    val locationName = arg<String>("location_name")

                    val positionResolver = arg<FinePositionResolver>("location")
                    val finePosition = positionResolver.resolve(source)

                    val rotationResolver = arg<RotationResolver>("rotation")
                    val rotation: Rotation = rotationResolver.resolve(source)

                    val location = Location(
                        null,
                        finePosition.x(),
                        finePosition.y(),
                        finePosition.z(),
                        rotation.yaw(),
                        rotation.pitch()
                    )

                    locationProperties[locationName]?.set(area, location)

                    source.sender.sendComponent {
                        translatable("command.success.admin.area.location_set") {
                            args {
                                string("area", area.name)
                            }
                        }
                    }

                    SINGLE_SUCCESS
                }
            }
            execute {
                val area = arg<GameArea>("area")
                val locationName = arg<String>("location_name")

                val resolver: FinePositionResolver = arg<FinePositionResolver>("location")
                val finePosition = resolver.resolve(source)

                val location = Location(null, finePosition.x(), finePosition.y(), finePosition.z())

                locationProperties[locationName]?.set(area, location)
                source.sender.sendComponent {
                    translatable("command.success.admin.area.location_set") {
                        args {
                            string("area", area.name)
                        }
                    }
                }

                SINGLE_SUCCESS
            }
        }

        guard {
            val locationName = arg<String>("location_name")

            if (locationName !in locationProperties) {
                source.sender.sendComponent {
                    translatable("command.error.admin.area.location_property_not_found") {
                        args {
                            string("location_name", locationName)
                        }
                    }
                }
                return@guard abort(NO_SUCCESS)
            }

            continueCommand()
        }
        literalExecute("@s") {
            val area = arg<GameArea>("area")
            val locationName = arg<String>("location_name")
            val location = source.location

            locationProperties[locationName]?.set(area, location)
            source.sender.sendComponent {
                translatable("command.success.admin.area.location_set_sender") {
                    args {
                        string("area", area.name)
                    }
                }
            }

            SINGLE_SUCCESS
        }
    }
}

private fun KtArgumentCommandBuilder<CommandSourceStack, String>.areaCreateCommand() = literalExecute(
    "create",
    runGuards = false // We don't want the guards to run because it would abort since the area doesn't exist at that point
) {
    val name = arg<String>("area")

    val success = GameAreaManager.createNewArea(name)

    if (success) {
        source.sender.sendComponent {
            translatable("command.success.admin.area.created") {
                args {
                    string("area", name)
                }
            }
        }
        return@literalExecute SINGLE_SUCCESS
    }

    source.sender.sendComponent {
        translatable("command.error.admin.area.already_exists") {
            args {
                string("area", name)
            }
        }
    }
    0
}