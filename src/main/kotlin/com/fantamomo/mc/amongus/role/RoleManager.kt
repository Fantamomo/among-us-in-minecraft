package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import kotlin.random.Random

class RoleManager(private val game: Game) {

    private val forcedRoles = mutableMapOf<AmongUsPlayer, Role<*, *>>()
    private val blockedRoles = mutableMapOf<AmongUsPlayer, MutableSet<Role<*, *>>>()
    private val allowedRoles = mutableMapOf<AmongUsPlayer, MutableSet<Role<*, *>>>()
    private val restrictedTeams = mutableMapOf<AmongUsPlayer, Team?>()

    fun start() {
        val players = game.players.toList()
        val imposterCount = game.settings[SettingsKey.ROLES.IMPOSTERS]
        require(imposterCount in 0..players.size)

        val roleChances = buildRoleChanceMap()

        for ((player, role) in forcedRoles) {
            player.assignedRole = role.assignTo(player)
        }

        val playersWithoutForced = players.filterNot { it in forcedRoles }

        val forcedImposters = forcedRoles.count { it.value.team == Team.IMPOSTERS }
        val remainingImposters = (imposterCount - forcedImposters).coerceAtLeast(0)

        val eligibleForImposter = playersWithoutForced.filter {
            restrictedTeams[it] != Team.CREWMATES
        }

        val imposters = eligibleForImposter
            .shuffled()
            .take(remainingImposters)

        val crewmates = playersWithoutForced - imposters.toSet()

        assignRoles(imposters, roleChances, Team.IMPOSTERS)
        assignRoles(crewmates, roleChances, Team.CREWMATES)

        for (player in players) {
            player.assignedRole?.onGameStart()
        }

        clearTemporaryState()
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

    private fun assignRoles(
        players: List<AmongUsPlayer>,
        roleChances: Map<Role<*, *>, Int>,
        team: Team
    ) {
        if (players.isEmpty()) return

        val teamRoles = roleChances.filterKeys { it.team == team }

        players.shuffled().forEach { player ->
            val role = pickRoleForPlayer(player, teamRoles, team)
            player.assignedRole = role.assignTo(player)
        }
    }

    private fun pickRoleForPlayer(
        player: AmongUsPlayer,
        teamRoles: Map<Role<*, *>, Int>,
        fallbackTeam: Team
    ): Role<*, *> {

        val whitelist = allowedRoles[player]
        var possibleRoles = if (!whitelist.isNullOrEmpty()) {
            teamRoles.filterKeys { it in whitelist }
        } else {
            teamRoles
        }

        restrictedTeams[player]?.let { restricted ->
            possibleRoles = possibleRoles.filterKeys { it.team == restricted }
        }

        blockedRoles[player]?.let { blocked ->
            possibleRoles = possibleRoles.filterKeys { it !in blocked }
        }

        if (possibleRoles.isEmpty()) {
            return fallbackTeam.defaultRole
        }

        val guaranteed = possibleRoles.filterValues { it == 100 }.keys.toList()
        if (guaranteed.isNotEmpty()) {
            return guaranteed.random()
        }

        val weighted = possibleRoles.filterValues { it in 1..99 }.toList()
        if (weighted.isEmpty()) {
            return fallbackTeam.defaultRole
        }

        return pickWeightedRole(weighted)
    }

    private fun pickWeightedRole(roles: List<Pair<Role<*, *>, Int>>): Role<*, *> {
        val totalWeight = roles.sumOf { it.second }
        require(totalWeight > 0)

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

    private fun buildRoleChanceMap(): Map<Role<*, *>, Int> =
        SettingsKey.ROLES.roles
            .mapValues { (_, key) ->
                game.settings[key].coerceIn(0, 100)
            }
            .filterValues { it > 0 }

    fun forceRole(player: AmongUsPlayer, role: Role<*, *>) {
        forcedRoles[player] = role
    }

    fun clearForcedRole(player: AmongUsPlayer) {
        forcedRoles.remove(player)
    }

    fun blockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles.computeIfAbsent(player) { mutableSetOf() }.add(role)
    }

    fun unblockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles[player]?.remove(role)
    }

    fun allowRole(player: AmongUsPlayer, role: Role<*, *>) {
        allowedRoles.computeIfAbsent(player) { mutableSetOf() }.add(role)
    }

    fun restrictTeam(player: AmongUsPlayer, team: Team?) {
        restrictedTeams[player] = team
    }

    fun clearRestrictions(player: AmongUsPlayer) {
        blockedRoles.remove(player)
        allowedRoles.remove(player)
        restrictedTeams.remove(player)
        forcedRoles.remove(player)
    }

    fun clearAllAdminData() {
        forcedRoles.clear()
        blockedRoles.clear()
        allowedRoles.clear()
        restrictedTeams.clear()
    }

    private fun clearTemporaryState() {
        forcedRoles.clear()
    }
}