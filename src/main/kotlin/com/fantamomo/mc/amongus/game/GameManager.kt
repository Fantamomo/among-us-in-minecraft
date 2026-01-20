package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.amongus.AmongUs

object GameManager {
    private val gamesByCode = mutableMapOf<String, Game>()
    private val games = mutableListOf<Game>()
    private var taskId = -1

    fun getGames(): List<Game> = games

    fun addGame(game: Game): Boolean {
        init()
        if (gamesByCode.containsKey(game.code)) return false
        games.add(game)
        gamesByCode[game.code] = game
        return true
    }

    fun init() {
        if (taskId != -1) return
        taskId = AmongUs.server.scheduler.scheduleSyncRepeatingTask(AmongUs, ::tick, 0L, 1L)
    }

    private fun tick() {
        if (games.isEmpty()) {
            AmongUs.server.scheduler.cancelTask(taskId)
            taskId = -1
        }
        games.forEach(Game::tick)
    }

    operator fun get(code: String): Game? = gamesByCode[code]
}