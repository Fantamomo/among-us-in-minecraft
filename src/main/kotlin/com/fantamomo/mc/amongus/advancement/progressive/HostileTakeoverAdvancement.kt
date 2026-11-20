package com.fantamomo.mc.amongus.advancement.progressive

import com.fantamomo.mc.amongus.advancement.ListenerAdvancement
import com.fantamomo.mc.amongus.advancement.ProgressiveAdvancement
import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.player.PlayerKillEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.util.KillerRole
import kotlinx.serialization.json.*
import java.util.*

object HostileTakeoverAdvancement :
    ProgressiveAdvancement<HostileTakeoverAdvancement, HostileTakeoverAdvancement.AssignedHostileTakeoverAdvancement>,
    ListenerAdvancement {

    override val id: String = "hostile_takeover"
    override val max: Int
        get() = killerRoleIds.size

    val killerRoleIds = Role.roles.filter { it is KillerRole }.map { it.id }

    override fun new(player: UUID) = AssignedHostileTakeoverAdvancement(player, mutableSetOf())

    override fun createOf(
        player: UUID,
        data: JsonElement
    ): AssignedHostileTakeoverAdvancement {
        val roles = mutableSetOf<String>()
        val jsonObject = data as? JsonObject
        if (jsonObject != null) {
            val complectedRoles = jsonObject["killed_by"] as? JsonArray
            if (complectedRoles != null) {
                for (role in complectedRoles.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }) {
                    roles.add(role)
                }
            }
        }
        return AssignedHostileTakeoverAdvancement(player, roles)
    }

    data class AssignedHostileTakeoverAdvancement(
        override val player: UUID,
        val killedBy: MutableSet<String>
    ) : ProgressiveAdvancement.AssignedProgressiveAdvancement<HostileTakeoverAdvancement, AssignedHostileTakeoverAdvancement>,
        ListenerAdvancement.AssignedListenerAdvancement {

        override val definition = HostileTakeoverAdvancement

        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false

        override val current: Int
            get() = killedBy.count { it in killerRoleIds }

        private var humanPlayer: HumanAmongUsPlayer? = null
        private var listenerHandler: ((PlayerKillEvent) -> Unit)? = null

        override fun toJson(): JsonElement {
            return buildJsonObject {
                putJsonArray("killed_by") {
                    killedBy.forEach { add(it) }
                }
            }
        }

        override fun start(player: HumanAmongUsPlayer) {
            require(player.uuid == this.player) { "$this is assigned to ${this.player} but was started by $player" }
            humanPlayer = player
            @Suppress("UNCHECKED_CAST")
            listenerHandler?.let { player.game.eventManager.unregister(it as (AmongUsEvent) -> Unit) }
            listenerHandler = handler@{ event ->
                if (isCompleted) return@handler

                if (event.victim == humanPlayer) {
                    val killerRole = event.player.role.definition
                    if (killerRole is KillerRole) {
                        killedBy.add(killerRole.id)
                        if (current == max) award()
                    }
                }
            }
            player.game.eventManager.register(listenerHandler!!)
        }

        override fun stop() {
            val humanAmongUsPlayer = humanPlayer ?: return
            @Suppress("UNCHECKED_CAST")
            listenerHandler?.let { humanAmongUsPlayer.game.eventManager.unregister(it as (AmongUsEvent) -> Unit) }
            listenerHandler = null
        }
    }
}