package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.brigadier.*
import com.mojang.brigadier.arguments.StringArgumentType
import org.bukkit.entity.Player

val AmongUsImposterMsgCommand = paperCommand("impostermsg") {
    requires { sender is Player }
    argument("message", StringArgumentType.greedyString()) {
        execute {
            val sender = source.sender as Player
            val amongUsPlayer = PlayerManager.getPlayer(sender)
            if (amongUsPlayer == null) {
                sendMessage {
                    translatable("command.error.impostermsg.not_joined")
                }
                return@execute 0
            }

            val game = amongUsPlayer.game

            if (game.phase == GamePhase.LOBBY || game.phase == GamePhase.STARTING) {
                sendMessage {
                    translatable("command.error.impostermsg.lobby")
                }
                return@execute 0
            }

            if (amongUsPlayer.assignedRole?.definition?.team != Team.IMPOSTERS) {
                sendMessage {
                    translatable("command.error.impostermsg.not_imposter")
                }
                return@execute 0
            }

            if (!game.settings[SettingsKey.MESSAGES.ALLOW_IMPOSTER_PRIVATE_MESSAGE]) {
                sendMessage {
                    translatable("command.error.impostermsg.disabled")
                }
                return@execute 0
            }

            if (!amongUsPlayer.isAlive) {
                sendMessage {
                    translatable("command.error.impostermsg.dead")
                }
                return@execute 0
            }

            val message = arg<String>("message")

            game.chatManager.sendImposterMessage(amongUsPlayer, message)

            SINGLE_SUCCESS
        }
    }
}