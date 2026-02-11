package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.statistics.StatisticMap
import com.fantamomo.mc.amongus.statistics.StatisticsManager
import kotlin.uuid.Uuid

class PlayerStatistics(uuid: Uuid) {
    private val statistics: StatisticMap = StatisticsManager.createOrLoad("player", uuid)

    val playTime = statistics.list("playTime")

    val statedGames = statistics.counter("statedGames")
    val playedGames = statistics.counter("playedGames")

    val winsAsImposter = statistics.counter("winsAsImposter")
    val winsAsCrewmate = statistics.counter("winsAsCrewmate")

    val losesAsImposter = statistics.counter("losesAsImposter")
    val losesAsCrewmate = statistics.counter("losesAsCrewmate")

    val winBySabotage = statistics.counter("winBySabotage")
    val loseBySabotage = statistics.counter("loseBySabotage")

    val tasksCompleted = statistics.counter("tasksCompleted")
    val fullyCompleteTasks = statistics.counter("fullyCompleteTasks")

    val killsAsImposter = statistics.counter("killsAsImposter")
    val killsAsImposterWhileSabotage = statistics.counter("killsAsImposterWhileSabotage")
    val killedByImposter = statistics.counter("killedByImposter")
    val killedByImposterWhileSabotage = statistics.counter("killedByImposterWhileSabotage")

    val ejected = statistics.counter("ejected")
    val ejectedWrong = statistics.counter("ejectedWrong")
    val ejectedCorrect = statistics.counter("ejectedCorrect")

    val voted = statistics.counter("voted")
    val votedSkip = statistics.counter("votedSkip")
    val votedCorrect = statistics.counter("votedCorrect")
    val votedWrong = statistics.counter("votedWrong")

    val timeUntilDead = statistics.list("timeUntilDead")
    val timeUntilVotedOut = statistics.list("timeUntilVotedOut")
    val timeUntilKilled = statistics.list("timeUntilKilled")

    val accused = statistics.counter("accused")
    val accusedCorrect = statistics.counter("accusedCorrect")
    val accusedWrong = statistics.counter("accusedWrong")

    val calledEmergency = statistics.counter("calledEmergency")
    val foundBodies = statistics.counter("foundBodies")
    val buttonPressed = statistics.counter("buttonPressed")


    internal fun onGameStart() {
        timeUntilDead.timerStart()
        timeUntilVotedOut.timerStart()
        timeUntilKilled.timerStart()
    }

    internal fun onGameStop() {
        timeUntilDead.timerReset()
        timeUntilVotedOut.timerReset()
        timeUntilKilled.timerReset()
    }

    companion object {
        val statistics = PlayerStatistics(Uuid.random()).statistics.run {
            getKeys().map { it to get(it)!!::class }
        }
    }
}