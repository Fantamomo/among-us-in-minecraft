package com.fantamomo.mc.amongus.advancement.progressive

import com.fantamomo.mc.amongus.advancement.ListenerAdvancement
import com.fantamomo.mc.amongus.advancement.ProgressiveAdvancement
import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.meeting.MeetingEndEvent
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Role
import kotlinx.serialization.json.*
import java.util.*

object GottaEjectEmAllAdvancement :
    ProgressiveAdvancement<GottaEjectEmAllAdvancement, GottaEjectEmAllAdvancement.AssignedGottaEjectEmAllAdvancement> {
    override val id: String = "gotta_eject_em_all"

    override val max: Int
        get() = Role.roles.size

    // this is used because I allow with the config.yml to disable roles, and without it, it would be possible to complete the advancement without having all roles
    private val roleIds: Set<String> = Role.roles.mapTo(mutableSetOf(), Role<*, *>::id)

    override fun new(player: UUID) = AssignedGottaEjectEmAllAdvancement(player, mutableSetOf())

    override fun createOf(
        player: UUID,
        data: JsonElement
    ): AssignedGottaEjectEmAllAdvancement {
        val roles = mutableSetOf<String>()
        val jsonObject = data as? JsonObject
        if (jsonObject != null) {
            val complectedRoles = jsonObject["complected_roles"] as? JsonArray
            if (complectedRoles != null) {
                for (role in complectedRoles.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }) {
                    roles.add(role)
                }
            }
        }
        return AssignedGottaEjectEmAllAdvancement(player, roles)
    }

    class AssignedGottaEjectEmAllAdvancement(
        override val player: UUID,
        val completedRoles: MutableSet<String>
    ) : ProgressiveAdvancement.AssignedProgressiveAdvancement<GottaEjectEmAllAdvancement, AssignedGottaEjectEmAllAdvancement>,
        ListenerAdvancement.AssignedListenerAdvancement {

        override val definition = GottaEjectEmAllAdvancement

        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false

        override val current: Int
            get() = completedRoles.count { it in roleIds }

        private var humanAmongUsPlayer: HumanAmongUsPlayer? = null
        private var listenerHandler: ((MeetingEndEvent) -> Unit)? = null

        override fun toJson(): JsonElement {
            return buildJsonObject {
                putJsonArray("complected_roles") {
                    for (role in completedRoles) {
                        add(role)
                    }
                }
            }
        }

        override fun start(player: HumanAmongUsPlayer) {
            this.humanAmongUsPlayer = player
            @Suppress("UNCHECKED_CAST")
            listenerHandler?.let { player.game.eventManager.unregister(it as (AmongUsEvent) -> Unit) }
            listenerHandler = handler@ { event ->
                if (isCompleted) return@handler
                val votes = (event.votes.entries.find { it.key.player == player }?.value as? MeetingManager.Vote.For)?.target
                if (votes != null && votes == event.ejectedPlayer) {
                    completedRoles.add(player.role.definition.id)
                    if (current == max) {
                        award()
                    }
                }
            }
            player.game.eventManager.register(listenerHandler!!)
        }

        override fun stop() {
            val humanAmongUsPlayer = humanAmongUsPlayer ?: return
            @Suppress("UNCHECKED_CAST")
            listenerHandler?.let { humanAmongUsPlayer.game.eventManager.unregister(it as (AmongUsEvent) -> Unit) }
            listenerHandler = null
        }

    }

}