package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.editStatistics
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

        val rolePool = buildRolePool()
        val unassigned = players.toMutableList()

        phase1AssignForced(unassigned)
        phase2AssignNeutralRestricted(unassigned, rolePool)
        phase3AssignNeutralRandom(unassigned, rolePool)
        phase4AssignTeams(unassigned, rolePool)

        for (player in players) {
            if (player.assignedRole == null) assign(player, Team.CREWMATES.defaultRole)
        }

        players.forEach { it.assignedRole?.onGameStart() }
        forcedRoles.clear()
    }

    fun end() = game.players.forEach { it.assignedRole?.onGameEnd() }

    fun tick() = game.players.forEach { it.assignedRole?.tick() }

    private fun phase1AssignForced(unassigned: MutableList<AmongUsPlayer>) {
        forcedRoles.forEach { (player, role) ->
            assign(player, role)
            unassigned.remove(player)
        }
    }

    private fun phase2AssignNeutralRestricted(
        unassigned: MutableList<AmongUsPlayer>,
        rolePool: RolePool
    ) {
        val neutralRestricted = unassigned.associateWith { restrictedTeams[it] as? Team.NEUTRAL }
        if (neutralRestricted.isEmpty()) return

        for ((player, team) in neutralRestricted) {
            if (team == null) continue
            val role = rolePool.pickFor(player, team) ?: Team.CREWMATES.defaultRole
            assign(player, role)
            unassigned.remove(player)
        }
    }

    private fun phase3AssignNeutralRandom(
        unassigned: MutableList<AmongUsPlayer>,
        rolePool: RolePool
    ) {
        val totalPlayers = game.players.size
        val imposterTarget = resolvedImposterTarget()

        val reservedSlots = imposterTarget + 1
        val maxNeutralSlots = (totalPlayers / 4).coerceAtLeast(0)

        var neutralsAssigned = 0

        for (role in rolePool.neutralRoles()) {
            if (neutralsAssigned >= maxNeutralSlots) break
            if (unassigned.size <= reservedSlots) break
            if (!rolePool.rolls(role)) continue

            val candidate = unassigned.firstOrNull { isEligibleFor(it, role) } ?: continue
            assign(candidate, role)
            unassigned.remove(candidate)
            neutralsAssigned++
        }
    }

    private fun phase4AssignTeams(
        unassigned: MutableList<AmongUsPlayer>,
        rolePool: RolePool
    ) {
        if (unassigned.isEmpty()) return

        val forcedImposterCount = forcedRoles.count { it.value.team == Team.IMPOSTERS }
        val targetImposters = (resolvedImposterTarget() - forcedImposterCount)
            .coerceIn(0, unassigned.size)

        val (imposterPool, crewPool) = splitByTeam(unassigned, targetImposters)

        imposterPool.forEach { assign(it, rolePool.pickFor(it, Team.IMPOSTERS) ?: Team.IMPOSTERS.defaultRole) }
        crewPool.forEach { assign(it, rolePool.pickFor(it, Team.CREWMATES) ?: Team.CREWMATES.defaultRole) }
    }

    private fun splitByTeam(
        players: List<AmongUsPlayer>,
        targetImposters: Int
    ): Pair<List<AmongUsPlayer>, List<AmongUsPlayer>> {
        val shuffled = players.shuffled()
        val preferredImposters = shuffled.filter { restrictedTeams[it] == Team.IMPOSTERS }
        val rest = shuffled.filter { restrictedTeams[it] != Team.IMPOSTERS }

        val imposters = (preferredImposters + rest).take(targetImposters)
        val crewmates = players - imposters.toSet()
        return imposters to crewmates
    }

    private fun resolvedImposterTarget(): Int {
        val total = game.players.size
        return game.settings[SettingsKey.ROLES.IMPOSTERS].coerceIn(0, total)
    }

    private fun assign(player: AmongUsPlayer, role: Role<*, *>) {
        player.assignedRole = role.assignTo(player)
        player.editStatistics {
            assignedRole[role]?.increment()
            assignedTeam[role.team]?.increment()
        }
    }

    private fun isEligibleFor(player: AmongUsPlayer, role: Role<*, *>): Boolean {
        val whitelist = allowedRoles[player]
        if (!whitelist.isNullOrEmpty() && role !in whitelist) return false

        val blocked = blockedRoles[player]
        if (blocked != null && role in blocked) return false

        val teamRestriction = restrictedTeams[player]
        return !(teamRestriction != null && teamRestriction != role.team)
    }

    private fun buildRolePool(): RolePool {
        val chances = SettingsKey.ROLES.ROLE_CHANCES.roles
            .mapValues { (_, key) -> game.settings[key].coerceIn(0, 100) }
            .filterValues { it > 0 }
        return RolePool(chances)
    }

    private inner class RolePool(private val chances: Map<Role<*, *>, Int>) {

        fun neutralRoles(): List<Role<*, *>> =
            chances.keys.filter { it.team is Team.NEUTRAL }.shuffled()

        fun rolls(role: Role<*, *>): Boolean {
            val chance = chances[role] ?: return false
            return chance >= 100 || Random.nextInt(100) < chance
        }

        fun pickFor(player: AmongUsPlayer, team: Team): Role<*, *>? {
            var candidates = chances.filterKeys { it.team == team }
            if (candidates.isEmpty()) return null

            val whitelist = allowedRoles[player]
            if (!whitelist.isNullOrEmpty()) candidates =
                candidates.filterKeys { it in whitelist }.ifEmpty { candidates }

            val blocked = blockedRoles[player]
            if (!blocked.isNullOrEmpty()) candidates = candidates.filterKeys { it !in blocked }.ifEmpty { candidates }

            if (candidates.isEmpty()) return null

            val guaranteed = candidates.filterValues { it >= 100 }.keys.toList()
            if (guaranteed.isNotEmpty()) return guaranteed.random()

            val weighted = candidates.filterValues { it in 1..99 }.toList()
            if (weighted.isNotEmpty()) return pickWeighted(weighted)

            return candidates.keys.randomOrNull()
        }

        private fun pickWeighted(roles: List<Pair<Role<*, *>, Int>>): Role<*, *> {
            val total = roles.sumOf { it.second }
            if (total <= 0) return roles.first().first

            var remaining = Random.nextInt(total)
            for ((role, weight) in roles) {
                remaining -= weight
                if (remaining < 0) return role
            }
            return roles.last().first
        }
    }

    fun forceRole(player: AmongUsPlayer, role: Role<*, *>) {
        forcedRoles[player] = role
        restrictedTeams.remove(player)
    }

    fun clearForcedRole(player: AmongUsPlayer) = forcedRoles.remove(player)

    fun blockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles.computeIfAbsent(player) { mutableSetOf() }.add(role)
        allowedRoles[player]?.remove(role)
    }

    fun unblockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles[player]?.remove(role)
    }

    fun allowRole(player: AmongUsPlayer, role: Role<*, *>) {
        allowedRoles.computeIfAbsent(player) { mutableSetOf() }.add(role)
        blockedRoles[player]?.remove(role)
    }

    fun restrictTeam(player: AmongUsPlayer, team: Team?) {
        if (player !in forcedRoles) restrictedTeams[player] = team
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
}