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
        if (players.isEmpty()) return

        val imposterCount = game.settings[SettingsKey.ROLES.IMPOSTERS].coerceIn(0, players.size)
        val roleChances = buildRoleChanceMap()

        for ((player, role) in forcedRoles) {
            player.assignedRole = role.assignTo(player)
        }

        val remaining = players.filterNot { it in forcedRoles }.toMutableList()

        val neutralRestricted = remaining.filter { restrictedTeams[it] is Team.NEUTRAL }.toMutableList()
        remaining.removeAll(neutralRestricted)

        for (player in neutralRestricted) {
            val targetTeam = restrictedTeams[player] as Team.NEUTRAL
            val matchingRole = roleChances.keys
                .firstOrNull { it.team == targetTeam && isEligibleFor(player, it) }

            player.assignedRole = (matchingRole ?: Team.CREWMATES.defaultRole).assignTo(player)
        }

        val neutralRoleChances = roleChances.entries
            .filter { it.key.team is Team.NEUTRAL }
            .shuffled()

        val shuffledPool = remaining.shuffled().toMutableList()

        for ((role, chance) in neutralRoleChances) {
            if (shuffledPool.isEmpty()) break

            val spawns = chance == 100 || Random.nextInt(100) < chance
            if (!spawns) continue

            val candidate = shuffledPool.firstOrNull { isEligibleFor(it, role) } ?: continue

            candidate.assignedRole = role.assignTo(candidate)
            shuffledPool.remove(candidate)
            remaining.remove(candidate)
        }

        val forcedImposters = forcedRoles.count { it.value.team == Team.IMPOSTERS }
        val targetImposters = when {
            forcedImposters >= imposterCount -> 0
            forcedImposters + remaining.size < imposterCount -> remaining.size
            else -> (imposterCount - forcedImposters).coerceAtLeast(0)
        }

        val eligibleForImposter = remaining.filter { player ->
            val teamRestriction = restrictedTeams[player]
            teamRestriction == null || teamRestriction == Team.IMPOSTERS
        }

        val imposters = if (eligibleForImposter.size >= targetImposters) {
            eligibleForImposter.shuffled().take(targetImposters)
        } else {
            remaining.shuffled().take(targetImposters)
        }

        val crewmates = remaining - imposters.toSet()

        assignRoles(imposters, roleChances, Team.IMPOSTERS)
        assignRoles(crewmates, roleChances, Team.CREWMATES)

        for (player in players) {
            if (player.assignedRole == null) {
                player.assignedRole = Team.CREWMATES.defaultRole.assignTo(player)
            }
        }

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
        var possibleRoles = teamRoles

        val whitelist = allowedRoles[player]
        if (!whitelist.isNullOrEmpty()) {
            val filtered = possibleRoles.filterKeys { it in whitelist }
            if (filtered.isNotEmpty()) possibleRoles = filtered
        }

        restrictedTeams[player]?.let { restricted ->
            val filtered = possibleRoles.filterKeys { it.team == restricted }
            if (filtered.isNotEmpty()) possibleRoles = filtered
        }

        blockedRoles[player]?.let { blocked ->
            val filtered = possibleRoles.filterKeys { it !in blocked }
            if (filtered.isNotEmpty()) possibleRoles = filtered
        }

        if (possibleRoles.isEmpty()) return fallbackTeam.defaultRole

        val guaranteed = possibleRoles.filterValues { it == 100 }.keys.toList()
        if (guaranteed.isNotEmpty()) return guaranteed.random()

        val weighted = possibleRoles.filterValues { it in 1..99 }.toList()
        if (weighted.isEmpty()) return possibleRoles.keys.firstOrNull() ?: fallbackTeam.defaultRole

        return pickWeightedRole(weighted)
    }

    private fun pickWeightedRole(roles: List<Pair<Role<*, *>, Int>>): Role<*, *> {
        if (roles.isEmpty()) error("No roles to pick from")

        val totalWeight = roles.sumOf { it.second }
        if (totalWeight <= 0) return roles.first().first

        val randomValue = Random.nextInt(totalWeight)
        var accumulated = 0

        for ((role, weight) in roles) {
            accumulated += weight
            if (randomValue < accumulated) return role
        }

        return roles.last().first
    }

    private fun isEligibleFor(player: AmongUsPlayer, role: Role<*, *>): Boolean {
        val whitelist = allowedRoles[player]
        if (!whitelist.isNullOrEmpty() && role !in whitelist) return false

        val blocked = blockedRoles[player]
        if (blocked != null && role in blocked) return false

        val teamRestriction = restrictedTeams[player]
        if (teamRestriction != null && teamRestriction != role.team) return false

        return true
    }

    private fun buildRoleChanceMap(): Map<Role<*, *>, Int> =
        SettingsKey.ROLES.roles
            .mapValues { (_, key) ->
                game.settings[key].coerceIn(0, 100)
            }
            .filterValues { it > 0 }

    fun forceRole(player: AmongUsPlayer, role: Role<*, *>) {
        forcedRoles[player] = role
        restrictedTeams.remove(player)
    }

    fun clearForcedRole(player: AmongUsPlayer) {
        forcedRoles.remove(player)
    }

    fun blockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles.computeIfAbsent(player) { mutableSetOf() }.add(role)
        allowedRoles[player]?.remove(role)
    }

    fun unblockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles[player]?.remove(role)
    }

    fun allowRole(player: AmongUsPlayer, role: Role<*, *>) {
        val allowed = allowedRoles.computeIfAbsent(player) { mutableSetOf() }
        allowed.add(role)
        blockedRoles[player]?.remove(role)
    }

    fun restrictTeam(player: AmongUsPlayer, team: Team?) {
        if (player !in forcedRoles) {
            restrictedTeams[player] = team
        }
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