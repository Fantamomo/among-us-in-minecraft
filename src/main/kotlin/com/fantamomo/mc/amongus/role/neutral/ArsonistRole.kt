package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.ArsonistAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.data.DistanceEnum
import net.kyori.adventure.text.format.NamedTextColor

object ArsonistRole : Role<ArsonistRole, ArsonistRole.AssignedArsonistRole> {
    override val id: String = "arsonist"
    override val team: Team = Team.NEUTRAL.ARSONIST
    override val defaultAbilities: Set<Ability<*, *>> = setOf(ArsonistAbility)

    override fun assignTo(player: AmongUsPlayer) = AssignedArsonistRole(player)

    class AssignedArsonistRole(override val player: AmongUsPlayer) : AssignedRole<ArsonistRole, AssignedArsonistRole> {
        override val definition = ArsonistRole

        val douseDistance: DistanceEnum
            get() = player.game.settings[SettingsKey.ROLES.ARSONIST_DOUSE_DISTANCE]

        val dousedPlayers: MutableSet<AmongUsPlayer> = mutableSetOf()

        override fun hasWon(): Boolean = player.isAlive && player.game.players.all { it === player || !it.isAlive || it in dousedPlayers }

        fun nearUndousedPlayer(): Boolean {
            if (!player.isAlive) return false
            val thisLoc = (player.mannequinController.getEntity() ?: player.livingEntity).location
            val douseDistanceSquared = douseDistance.distance.let { it * it }

            for (otherPlayer in player.game.players) {
                if (otherPlayer === player) continue
                if (!otherPlayer.isAlive) continue
                if (otherPlayer in dousedPlayers) continue
                val loc = (otherPlayer.mannequinController.getEntity() ?: otherPlayer.livingEntity).location
                if (thisLoc.distanceSquared(loc) < douseDistanceSquared) return true
            }
            return false
        }

        fun douseNearest() {
            if (!player.isAlive) return
            val thisLoc = (player.mannequinController.getEntity() ?: player.livingEntity).location
            val douseDistanceSquared = douseDistance.distance.let { it * it }

            var nearestDistance: Double = Double.MAX_VALUE
            var nearest: AmongUsPlayer? = null

            for (otherPlayer in player.game.players) {
                if (otherPlayer === player) continue
                if (!otherPlayer.isAlive) continue
                if (otherPlayer in dousedPlayers) continue
                val loc = (otherPlayer.mannequinController.getEntity() ?: otherPlayer.livingEntity).location
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
            this.player.statistics.arsonistDousedPlayers.increment()
            player.statistics.arsonistDoused.increment()
            val mannequinController = player.mannequinController
            val player = this.player.player
            if (player != null) {
                mannequinController.setNameColorFor(player, NamedTextColor.BLACK)
            } else {
                mannequinController.setNameColorFor(this.player.uuid, NamedTextColor.BLACK)
            }
        }
    }
}