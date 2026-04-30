package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.log.elements.AssignActionElements
import org.slf4j.LoggerFactory
import kotlin.random.Random

class RoleManager(private val game: Game) {

    private val forcedRoles: MutableMap<AbstractAmongUsPlayer, Role<*, *>> = mutableMapOf()
    private val blockedRoles: MutableMap<AbstractAmongUsPlayer, MutableSet<Role<*, *>>> = mutableMapOf()
    private val allowedRoles: MutableMap<AbstractAmongUsPlayer, MutableSet<Role<*, *>>> = mutableMapOf()
    private val restrictedTeams: MutableMap<AbstractAmongUsPlayer, Team?> = mutableMapOf()

    fun assign() {
        @Suppress("UNCHECKED_CAST")
        val players = game.players.toList() as List<AbstractAmongUsPlayer>
        if (players.isEmpty()) return

        val assigner = RoleAssigner(this, players.shuffled(), forcedRoles, blockedRoles, allowedRoles, restrictedTeams)

        assigner.assign()
    }

    fun start() {
        game.players.forEach { it.role.onGameStart() }
    }

    fun end() = game.players.forEach { it.role.onGameEnd() }

    fun tick(tickContext: TickContext) = game.players.forEach { it.internal.assignedRole?.tick(tickContext) }


    private class RoleAssigner(
        val manger: RoleManager,
        val players: List<AbstractAmongUsPlayer>,
        val forcedRoles: Map<AbstractAmongUsPlayer, Role<*, *>>,
        val blockedRoles: Map<AbstractAmongUsPlayer, Set<Role<*, *>>>,
        val allowedRoles: Map<AbstractAmongUsPlayer, Set<Role<*, *>>>,
        val restrictedTeams: Map<AbstractAmongUsPlayer, Team?>,
    ) {
        private val chances: Map<Role<*, *>, Int> = SettingsKey.ROLES.ROLE_CHANCES.roles
            .mapValues { (_, key) -> manger.game.settings[key].coerceIn(0, 100) }
            .filterValues { it > 0 }

        private val equalChances = isEqualChances(chances)

        private val unassigned = players.toMutableList()

        private val assignedNeutralRoles: MutableSet<Role<*, *>> = mutableSetOf()

        fun assign() {
            if (unassigned.isEmpty()) throw IllegalStateException("No players to assign roles to.")

            phase1AssignForced()
            phase2AssignNeutralRestricted()
            phase3AssignNeutralRandom()
            phase4AssignTeams()
            for (player in players) {
                if (player.assignedRole == null) assignRole(player, Team.CREWMATES.defaultRole)
            }
            unassigned.clear()
        }

        private fun phase1AssignForced() {
            forcedRoles.forEach { (player, role) ->
                assignRole(player, role)
                unassigned.remove(player)
            }
        }

        private fun phase2AssignNeutralRestricted() {
            val neutralRestricted = unassigned.associateWith { restrictedTeams[it] as? Team.NEUTRAL }
            if (neutralRestricted.isEmpty()) return

            for ((player, team) in neutralRestricted) {
                if (team == null) continue
                val role = pickFor(player, team) ?: continue
                assignRole(player, role)
                unassigned.remove(player)
            }
        }

        private fun phase3AssignNeutralRandom() {
            val totalPlayers = manger.game.players.size
            val imposterTarget = resolvedImposterTarget()

            val reservedSlots = imposterTarget + 1
            val maxNeutralSlots = (if (totalPlayers == 4) (totalPlayers / 4).takeIf { Random.nextDouble() > 0.7 }
                ?: 0 else totalPlayers / 5).coerceAtLeast(0)

            if (maxNeutralSlots == 0) return

            var neutralsAssigned = players.count { restrictedTeams[it] == Team.NEUTRAL }

            if (neutralsAssigned >= maxNeutralSlots) return

            val neutralRoles = neutralRoles().filter { it !in assignedNeutralRoles }

            for (role in neutralRoles) {
                if (neutralsAssigned >= maxNeutralSlots) break
                if (unassigned.size <= reservedSlots) break
                if (!rolls(role)) continue

                val candidate = unassigned.firstOrNull { isEligibleFor(it, role) } ?: continue
                assignRole(candidate, role)
                unassigned.remove(candidate)
                neutralsAssigned++
            }
        }

        private fun phase4AssignTeams() {
            if (unassigned.isEmpty()) return

            val forcedImposterCount = forcedRoles.count { it.value.team == Team.IMPOSTERS }
            val targetImposters = (resolvedImposterTarget() - forcedImposterCount)
                .coerceIn(0, unassigned.size)

            val (imposterPool, crewPool) = splitByTeam(unassigned, targetImposters)

            imposterPool.forEach { assignRole(it, pickFor(it, Team.IMPOSTERS) ?: Team.IMPOSTERS.defaultRole) }
            crewPool.forEach { assignRole(it, pickFor(it, Team.CREWMATES) ?: Team.CREWMATES.defaultRole) }
        }

        private fun splitByTeam(
            players: List<AbstractAmongUsPlayer>,
            targetImposters: Int
        ): Pair<List<AbstractAmongUsPlayer>, List<AbstractAmongUsPlayer>> {
            val shuffled = players.shuffled()
            val preferredImposters = shuffled.filter { restrictedTeams[it] == Team.IMPOSTERS }
            val rest = shuffled.filter { restrictedTeams[it] != Team.IMPOSTERS }

            val imposters = (preferredImposters + rest).take(targetImposters)
            val crewmates = players - imposters.toSet()
            return imposters to crewmates
        }

        private fun resolvedImposterTarget(): Int {
            val total = manger.game.players.size
            return manger.game.settings[SettingsKey.ROLES.IMPOSTERS].coerceIn(0, total)
        }

        private fun assignRole(player: AbstractAmongUsPlayer, role: Role<*, *>) {
            if (player.assignedRole != null) {
                logger.warn("Player ${player.name} already has a role assigned. Overwriting.")
            }
            player.assignedRole = role.assignTo(player)
            manger.game.actionLog.add(AssignActionElements.AssignRole(player.uuid, role.id))
            if (player.isHuman) player.editStatistics {
                assignedRole[role]?.increment()
                assignedTeam[role.team]?.increment()
            }
        }

        private fun isEligibleFor(player: AbstractAmongUsPlayer, role: Role<*, *>): Boolean {
            val whitelist = allowedRoles[player]
            if (!whitelist.isNullOrEmpty() && role !in whitelist) return false

            val blocked = blockedRoles[player]
            if (blocked != null && role in blocked) return false

            val teamRestriction = restrictedTeams[player]
            return !(teamRestriction != null && teamRestriction != role.team)
        }

        private fun neutralRoles(): List<Role<*, *>> =
            chances.keys.filter { it.team is Team.NEUTRAL }.shuffled()

        private fun rolls(role: Role<*, *>): Boolean {
            val chance = chances[role] ?: return false
            return chance >= 100 || Random.nextInt(100) < chance
        }

        fun pickFor(player: AbstractAmongUsPlayer, team: Team): Role<*, *>? {
            var candidates = chances.filterKeys { it.team == team }
            if (candidates.isEmpty()) return null

            val whitelist = allowedRoles[player]
            if (!whitelist.isNullOrEmpty()) candidates =
                candidates.filterKeys { it in whitelist }.ifEmpty { candidates }

            val blocked = blockedRoles[player]
            if (!blocked.isNullOrEmpty()) candidates =
                candidates.filterKeys { it !in blocked }.ifEmpty { candidates }

            if (candidates.isEmpty()) return null // should never be true

            if (equalChances) return candidates.keys.random()

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
        forcedRoles[player.internal] = role
        restrictedTeams.remove(player)
    }

    fun clearForcedRole(player: AmongUsPlayer) = forcedRoles.remove(player)

    fun blockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles.computeIfAbsent(player.internal) { mutableSetOf() }.add(role)
        allowedRoles[player]?.remove(role)
    }

    fun unblockRole(player: AmongUsPlayer, role: Role<*, *>) {
        blockedRoles[player]?.remove(role)
    }

    fun allowRole(player: AmongUsPlayer, role: Role<*, *>) {
        allowedRoles.computeIfAbsent(player.internal) { mutableSetOf() }.add(role)
        blockedRoles[player]?.remove(role)
    }

    fun restrictTeam(player: AmongUsPlayer, team: Team?) {
        if (player !in forcedRoles) restrictedTeams[player.internal] = team
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

    companion object {
        private val logger = LoggerFactory.getLogger("RoleManager")

        private fun isEqualChances(chances: Map<Role<*, *>, Int>): Boolean {
            val iterator = chances.values.iterator()
            if (!iterator.hasNext()) return true
            val first = iterator.next()
            for (element in iterator) {
                if (element != first) return false
            }
            return true
        }
    }
}