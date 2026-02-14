package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import kotlin.random.Random

class RoleManager(private val game: Game) {

    fun start() {
        val imposterCount = game.settings[SettingsKey.ROLES.IMPOSTERS]
        require(imposterCount in 0..game.players.size)

        val roleChances = buildRoleChanceMap()

        val shuffledPlayers = game.players.shuffled()

        val imposters = shuffledPlayers.take(imposterCount)
        val crewmates = shuffledPlayers.drop(imposterCount)

        assignRoles(
            imposters,
            roleChances.filterKeys { it.team == Team.IMPOSTERS },
            Team.IMPOSTERS
        )

        assignRoles(
            crewmates,
            roleChances.filterKeys { it.team == Team.CREWMATES },
            Team.CREWMATES
        )

        for (player in game.players) {
            player.assignedRole?.onGameStart()
        }
    }

    fun end() {
        for (player in game.players) {
            player.assignedRole?.onGameEnd()
        }
    }

    fun tick() {
        for (player in game.players) {
            player.assignedRole?.tick()
        }
    }

    private fun buildRoleChanceMap(): Map<Role<*, *>, Int> =
        SettingsKey.ROLES.roles
            .mapValues { (_, key) ->
                game.settings[key].coerceIn(0, 100)
            }
            .filterValues { it > 0 }

    private fun assignRoles(
        players: List<AmongUsPlayer>,
        roles: Map<Role<*, *>, Int>,
        team: Team
    ) {
        if (players.isEmpty()) return

        if (roles.isEmpty()) {
            players.forEach { player ->
                player.assignedRole = team.defaultRole.assignTo(player)
            }
            return
        }

        val guaranteedRoles = roles
            .filterValues { it == 100 }
            .keys
            .toMutableList()
            .apply { shuffle() }

        val weightedRoles = roles
            .filterValues { it in 1..99 }
            .toList()
            .shuffled()

        val shuffledPlayers = players.shuffled()
        val assignedPlayers = mutableSetOf<AmongUsPlayer>()

        for ((index, role) in guaranteedRoles.withIndex()) {
            if (index >= shuffledPlayers.size) break

            val player = shuffledPlayers[index]
            player.assignedRole = role.assignTo(player)
            assignedPlayers += player
        }

        val remainingPlayers = shuffledPlayers.filterNot { it in assignedPlayers }

        if (weightedRoles.isEmpty()) {
            remainingPlayers.forEach { player ->
                player.assignedRole = team.defaultRole.assignTo(player)
            }
            return
        }

        remainingPlayers.forEach { player ->
            val role = pickWeightedRole(weightedRoles)
            player.assignedRole = role.assignTo(player)
        }
    }

    private fun pickWeightedRole(roles: List<Pair<Role<*, *>, Int>>): Role<*, *> {
        val totalWeight = roles.sumOf { it.second }
        require(totalWeight > 0) { "No weighted roles available." }

        val randomValue = Random.nextInt(totalWeight)
        var accumulated = 0

        for ((role, weight) in roles) {
            accumulated += weight
            if (randomValue < accumulated) {
                return role
            }
        }

        error("Weighted role selection failed.")
    }
}