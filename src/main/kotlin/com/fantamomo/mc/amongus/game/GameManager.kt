package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.amongus.AmongUs

object GameManager {
    private val games = mutableListOf<Game>()
    private var taskId = -1

    fun getGames(): List<Game> = games

    fun addGame(game: Game): Boolean {
        init()
        return games.add(game)
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
}