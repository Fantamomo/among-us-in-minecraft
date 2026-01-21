package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import kotlin.random.Random

class RoleManager(val game: Game) {

    fun start() {
        val impostersCount: Int = game.settings[SettingsKey.IMPOSTERS]

        val roleChances: Map<Role<*, *>, Int> =
            SettingsKey.roles.mapValues { game.settings[it.value] }

        val imposterRoles = roleChances.filter { it.key.team == Team.IMPOSTERS }
        val crewmateRoles = roleChances.filter { it.key.team == Team.CREWMATES }

        val shuffledPlayers: List<AmongUsPlayer> = game.players.shuffled()

        val imposters = shuffledPlayers.take(impostersCount)
        val crewmates = shuffledPlayers.drop(impostersCount)

        imposters.forEach { player ->
            val role = pickWeightedRole(imposterRoles)
            player.assignedRole = role.assignTo(player)
        }

        crewmates.forEach { player ->
            val role = pickWeightedRole(crewmateRoles)
            player.assignedRole = role.assignTo(player)
        }
    }

    private fun pickWeightedRole(roles: Map<Role<*, *>, Int>): Role<*, *> {
        val totalWeight = roles.values.sum()
        require(totalWeight > 0) { "Total role weight must be greater than 0" }

        val randomValue = Random.nextInt(totalWeight)
        var currentWeight = 0

        for ((role, weight) in roles) {
            currentWeight += weight
            if (randomValue < currentWeight) {
                return role
            }
        }

        error("Weighted role selection failed")
    }
}
