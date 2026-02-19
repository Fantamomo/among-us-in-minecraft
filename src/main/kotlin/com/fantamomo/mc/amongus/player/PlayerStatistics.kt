package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.marker.KillerRole
import com.fantamomo.mc.amongus.statistics.CounterStatistic
import com.fantamomo.mc.amongus.statistics.StatisticMap
import com.fantamomo.mc.amongus.statistics.StatisticsManager
import kotlin.uuid.Uuid

class PlayerStatistics(uuid: Uuid) {
    private val statistics: StatisticMap = StatisticsManager.createOrLoad("player", uuid)

    val playTime = statistics.list("play_Time")

    val statedGames = statistics.counter("stated_games")
    val playedGames = statistics.counter("played_games")
    val survivedGames = statistics.counter("survived_games")

    val winsAs: Map<Role<*, *>, CounterStatistic> = Role.roles.associateWith { statistics.counter("win_as_${it.id}") }
    val winsWith: Map<Team, CounterStatistic> = Team.teams.associateWith { statistics.counter("win_with_${it.id}") }

    val losesAs: Map<Role<*, *>, CounterStatistic> = Role.roles.associateWith { statistics.counter("lose_as_${it.id}") }
    val losesWith: Map<Team, CounterStatistic> = Team.teams.associateWith { statistics.counter("lose_with_${it.id}") }

    val winBySabotage = statistics.counter("win_by_sabotage")
    val loseBySabotage = statistics.counter("lose_by_sabotage")

    val tasksCompleted = statistics.counter("tasks_completed")
    val fullyCompleteTasks = statistics.counter("fully_complete_tasks")

    val kills: Map<Role<*, *>, CounterStatistic> = Role.roles.filter { it is KillerRole }.associateWith { statistics.counter("kill_as_${it.id}") }
    val killed: Map<Role<*, *>, CounterStatistic> = Role.roles.filter { it is KillerRole }.associateWith { statistics.counter("killed_by_${it.id}") }

    val killsAsImposterWhileSabotage = statistics.counter("kills_as_imposter_while_sabotage")
    val killsWhileMorphed = statistics.counter("kills_while_morphed")
    val killsInGhostForm = statistics.counter("kills_in_ghost_form")

    val killedByImposterWhileSabotage = statistics.counter("killed_by_imposter_while_sabotage")
    val killedByMorphedPlayer = statistics.counter("killed_by_morphed_player")
    val killedBySheriffCorrect = statistics.counter("killed_by_sheriff_correct")
    val killedBySheriffWrong = statistics.counter("killed_by_sheriff_wrong")
    val killedAsSheriffCorrect = statistics.counter("killed_as_sheriff_correct")
    val killedAsSheriffWrong = statistics.counter("killed_as_sheriff_wrong")
    val killedByPlayerInGhostForm = statistics.counter("killed_by_player_in_ghost_form")

    val cannibalEatenBodies = statistics.counter("cannibal_eaten_bodies")
    val minerCreatedVents = statistics.counter("miner_created_vents")

    val ejected = statistics.counter("ejected")
    val ejectedWrong = statistics.counter("ejected_wrong")
    val ejectedCorrect = statistics.counter("ejected_correct")
    val ejectedAs: Map<Role<*, *>, CounterStatistic> = Role.roles.associateWith { statistics.counter("ejected_as_${it.id}") }

    val voted = statistics.counter("voted")
    val votedSkip = statistics.counter("voted_skip")
    val votedCorrect = statistics.counter("voted_correct")
    val votedWrong = statistics.counter("voted_wrong")
    val votedAt: Map<Role<*, *>, CounterStatistic> = Role.roles.associateWith { statistics.counter("voted_at_${it.id}") }
    val votedSelf = statistics.counter("voted_self")

    val timeUntilDead = statistics.list("time_until_dead")
    val timeUntilVotedOut = statistics.list("time_until_voted_out")
    val timeUntilKilled = statistics.list("time_until_killed")

    val accused = statistics.counter("accused")
    val accusedCorrect = statistics.counter("accused_correct")
    val accusedWrong = statistics.counter("accused_wrong")
    val accusedAs: Map<Role<*, *>, CounterStatistic> = Role.roles.associateWith { statistics.counter("accused_as_${it.id}") }

    val calledEmergency = statistics.counter("called_emergency")
    val foundBodies = statistics.counter("found_bodies")
    val buttonPressed = statistics.counter("button_pressed")
    val emergencyMeetingsSurvived = statistics.counter("emergency_meetings_survived")

    val assignedRole: Map<Role<*, *>, CounterStatistic> = Role.roles.associateWith { statistics.counter("assigned_role_${it.id}") }
    val assignedTeam: Map<Team, CounterStatistic> = Team.teams.associateWith { statistics.counter("assigned_team_${it.id}") }

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
        // Create a temporary PlayerStatistics instance to extract statistic metadata
        // (statistic keys mapped to their types) without persisting to disk
        val statistics = PlayerStatistics(Uuid.random()).statistics.run {
            StatisticsManager.unregister(this) // so it doesn't get saved'
            getKeys().associateWith { get(it)!!::class }
        }
    }
}