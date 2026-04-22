package com.fantamomo.mc.amongus.util

import com.fantamomo.mc.amongus.player.BotAmongUsPlayer

object BotsJoinMessages {
    private val messages: List<String>

    init {
        val url = this::class.java.classLoader.getResource("util/bots-join-messages.txt")
        var messages: List<String> = listOf()
        if (url != null) {
            try {
                val connection = url.openConnection()
                connection.setUseCaches(false)
                val stream = connection.getInputStream()
                messages = stream?.use { input ->
                    input.bufferedReader()
                        .readLines()
                        .filter {
                            it.isNotBlank() && it.length != 37
                        }
                } ?: listOf()
            } catch (e: Exception) {
            }
        }
        this.messages = messages
    }

    fun getRandomMessage(bot: BotAmongUsPlayer): String {
        val message = messages.randomOrNull() ?: return "My name is ${bot.name}!"
        return message
            .replace("<name>", bot.name)
            .replace("<color>", bot.color.toString().lowercase())
            .replace("<code>", bot.game.code)
            .replace("<players>", bot.game.players.size.toString())
    }
}