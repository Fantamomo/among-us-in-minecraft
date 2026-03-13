@file:Suppress("DEPRECATION")

package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import io.papermc.paper.event.player.ChatEvent
import net.kyori.adventure.text.Component

class ChatManager(val game: Game) {
    private var disableRestriction = false

    fun onChat(sender: AmongUsPlayer, event: ChatEvent) {
        event.isCancelled = true
        val message = event.message()
        if (game.phase == GamePhase.LOBBY || game.phase == GamePhase.STARTING) {
            sendLobbyMessage(sender, message)
            return
        }
        val meetingManager = game.meetingManager
        if (!meetingManager.isCurrentlyAMeeting() &&
            (sender.isAlive || !game.settings[SettingsKey.MESSAGES.ALLOW_GHOST_MESSAGE_IN_GAME])
        ) {
            if (disableRestriction) {
                val message = getMessage("chat.message.in_game", sender, message)
                game.audienceAll.sendMessage(message)
                game.logger.info(message)
            } else {
                sender.player?.sendMessage(ERROR_IN_GAME)
            }
            return
        }
        if (!sender.isAlive) {
            sendGhostMessage(sender, message)
            return
        }
        sendMeetingMessage(sender, message)
    }

    fun sendMeetingMessage(sender: AmongUsPlayer, message: Component) {
        val message = getMessage("chat.message.meeting", sender, message)
        game.sendChatMessage(message)
        game.logger.info(message)
    }

    fun sendGhostMessage(sender: AmongUsPlayer, input: Component) {
        val message = getMessage("chat.message.ghost", sender, input)
        val audience = if (disableRestriction) game.audienceAll else game.audienceDead
        audience.sendMessage(message)
        game.logger.info(message)
    }

    fun sendLobbyMessage(sender: AmongUsPlayer, input: Component) {
        val message = getMessage("chat.message.lobby", sender, input)
        game.sendChatMessage(message)
        game.logger.info(message)
    }

    private fun getMessage(key: String, player: AmongUsPlayer, message: Component) = textComponent {
        translatable(key) {
            args {
                component("player", player.player?.displayName() ?: Component.text(player.name))
                component("message", message)
            }
        }
    }

    fun sendImposterMessage(player: AmongUsPlayer, message: String) {
        val component = getMessage("chat.message.imposter", player, Component.text(message))
        game.audienceImposter.sendMessage(component)
        game.logger.info(component)
    }

    fun start() {
        disableRestriction = game.settings[SettingsKey.MESSAGES.DISABLE_MESSAGES_RESTRICTION]
    }

    companion object {
        private val ERROR_IN_GAME = Component.translatable("chat.error.in_game")
    }
}