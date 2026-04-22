package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.newLine
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.ArsonistAbility
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.data.DistanceEnum
import com.fantamomo.mc.amongus.util.log.elements.CustomAbilityActionElements
import net.kyori.adventure.text.format.NamedTextColor

object ArsonistRole : Role<ArsonistRole, ArsonistRole.AssignedArsonistRole> {
    override val id: String = "arsonist"
    override val team: Team = Team.NEUTRAL.ARSONIST
    override val defaultAbilities: Set<Ability<*, *>> = setOf(ArsonistAbility)

    override fun assignTo(player: AmongUsPlayer) = AssignedArsonistRole(player)

    class AssignedArsonistRole(override val player: AmongUsPlayer) : AssignedRole<ArsonistRole, AssignedArsonistRole>, SupportBotsRole {
        override val definition = ArsonistRole

        val douseDistance: DistanceEnum
            get() = player.game.settings[SettingsKey.ROLES.ARSONIST.DOUSE_DISTANCE]

        val dousedPlayers: MutableSet<AmongUsPlayer> = mutableSetOf()

        override fun hasWon(): Boolean = player.isAlive() && player.game.players.all { it === player || !it.isAlive() || it in dousedPlayers }

        fun nearUndousedPlayer(): Boolean {
            if (!player.isAlive()) return false
            val thisLoc = player.location
            val douseDistanceSquared = douseDistance.distance.let { it * it }

            for (otherPlayer in player.game.players) {
                if (otherPlayer === player) continue
                if (!otherPlayer.isAlive()) continue
                if (otherPlayer.isVented()) continue
                if (otherPlayer in dousedPlayers) continue
                val loc = otherPlayer.location
                if (thisLoc.distanceSquared(loc) < douseDistanceSquared) return true
            }
            return false
        }

        fun douseNearest() {
            if (!player.isAlive()) return
            val thisLoc = player.location
            val douseDistanceSquared = douseDistance.distance.let { it * it }

            var nearestDistance: Double = Double.MAX_VALUE
            var nearest: AmongUsPlayer? = null

            for (otherPlayer in player.game.players) {
                if (otherPlayer === player) continue
                if (!otherPlayer.isAlive()) continue
                if (otherPlayer.isVented()) continue
                if (otherPlayer in dousedPlayers) continue
                val loc = otherPlayer.location
                val distanceSquared = thisLoc.distanceSquared(loc)
                if (distanceSquared < douseDistanceSquared && distanceSquared < nearestDistance) {
                    nearest = otherPlayer
                    nearestDistance = distanceSquared
                }
            }
            nearest?.let(::douse)
        }

        private fun douse(player: AmongUsPlayer) {
            dousedPlayers += player
            player.game.actionLog.add(CustomAbilityActionElements.ArsonistDouse(this.player.uuid, player.uuid))
            this.player.humanOrNull?.statistics?.arsonistDousedPlayers?.increment()
            player.humanOrNull?.statistics?.arsonistDoused?.increment()
            val mannequinController = player.mannequinController
            val player = this.player.humanOrNull?.player
            if (player != null) {
                mannequinController.setNameColorFor(player, NamedTextColor.BLACK)
            } else if (this.player.isHuman) {
                mannequinController.setNameColorFor(this.player.uuid, NamedTextColor.BLACK)
            }
        }

        override fun gameEndInfo() = textComponent {
            translatable("role.arsonist.end.doused") {
                args { numeric("count", dousedPlayers.size) }
            }
            newLine()
            translatable("role.arsonist.end.remaining") {
                args {
                    val left = player.game.players.size - dousedPlayers.count { it.isAlive() } - 1
                    numeric("count", left)
                }
            }
        }

        override fun createBotVoteTargetController() = ArsonistBotVoteTargetController(this)
    }

    /**
     * A controller that will filter out players that have already been doused.
     *
     * The arsonist will prioritize voting for players that have not been doused, so he can reduce the count of doused players faster and increase the chance of winning.
     */
    class ArsonistBotVoteTargetController(val role: AssignedArsonistRole) : BotVoteTargetController(
        role.player.botOrNull
            ?: throw IllegalArgumentException("Player must be a bot to create SnitchBotVoteTargetController")
    ) {
        private val random = default(role.player)

        override fun getTarget(availableTargets: List<AmongUsPlayer>): Target? = getTarget(availableTargets, true)

        override fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean): Target? {
            val filteredTargets = availableTargets.filterNot { role.dousedPlayers.contains(it) }
            return random.getTarget(filteredTargets, includeSkip)
        }
    }
}