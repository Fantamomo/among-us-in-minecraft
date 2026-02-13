package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.abilities.ReportAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.*
import kotlin.uuid.toKotlinUuid

class AmongUsPlayer internal constructor(
    val uuid: UUID,
    name: String,
    val game: Game,
    val locationBeforeGame: Location? = null
) {
    private var _name: String = name
    private var _locale: Locale = Locale.getDefault()
    private var _profile: PlayerProfile? = null
    internal val abilities: MutableList<AssignedAbility<*, *>> = mutableListOf()

    val mannequinController = MannequinController(this)

    val name: String
        get() = player?.name?.also { _name = it } ?: _name
    val livingEntityOrNull: LivingEntity?
        get() = player ?: mannequinController.getEntity()
    val livingEntity: LivingEntity
        get() = livingEntityOrNull ?: throw IllegalStateException("No living entity available for $name ($uuid)")
    var player: Player? = null
        set(value) {
            field = value
            if (value != null) {
                _profile = value.playerProfile
            }
        }
    var color: PlayerColor = game.randomPlayerColor()
        set(value) {
            field = value
            val helmet = value.toItemStack()
            player?.inventory?.helmet = helmet
            mannequinController.getEntity()?.equipment?.helmet = helmet
        }
    val locale: Locale
        get() = player?.locale()?.also { _locale = it } ?: _locale
    val profile: PlayerProfile
        get() = player?.playerProfile?.also { _profile = it } ?: _profile ?: throw IllegalStateException("No profile available")
    var assignedRole: AssignedRole<*, *>? = null
    val tasks: MutableSet<TaskManager.RegisteredTask>
        get() = game.taskManager.get(this)
    var isAlive: Boolean = true
    var meetingButtonsPressed: Int = 0
    val canDoTasks: Boolean
        get() = assignedRole?.definition?.canDoTask != false

    val statistics = PlayerStatistics(uuid.toKotlinUuid())

    private fun checkGameRunning() {
        if (game.phase != GamePhase.RUNNING) throw IllegalStateException("Cannot perform this action in this phase")
    }

    internal fun notifyAbilityItemChange(item: AbilityItem) {
        // checkGameRunning()
        val player = player ?: return
        val uuid = item.uuid
        val slots = player.inventory.mapIndexedNotNull { index, stack ->
            index.takeIf {
                stack?.persistentDataContainer?.get(
                    AbilityItem.ABILITY_UUID,
                    CustomPersistentDataTypes.UUID
                ) == uuid
            }
        }
        val new = item.get()
        for (slot in slots) {
            player.inventory.setItem(slot, new)
        }
        if (player.itemOnCursor.persistentDataContainer.get(
                AbilityItem.ABILITY_UUID,
                CustomPersistentDataTypes.UUID
            ) == uuid
        ) {
            player.setItemOnCursor(new)
        }
    }

    fun addNewAbility(ability: Ability<*, *>) {
        val assigned = ability.assignTo(this)
        AbilityManager.registerAbility(assigned)
        abilities.add(assigned)
        if (game.phase == GamePhase.LOBBY) return
        val player = player
        if (player != null) {
            for (item in assigned.items) {
                item.startCooldown()
                player.inventory.addItem(item.get())
            }
        }
    }

    fun isVented() = game.ventManager.isVented(this)

    fun hasAbility(ability: Ability<*, *>) = abilities.any { it.definition === ability }

    @Suppress("UNCHECKED_CAST")
    fun <A : Ability<A, S>, S : AssignedAbility<A, S>> getAssignedAbility(ability: A) =
        abilities.firstOrNull { it.definition === ability } as? S

    fun isInVent(): Boolean = game.ventManager.isNearVent(this)

    fun isInCams(): Boolean = game.cameraManager.isInCams(this)

    fun canSeeWhenLightsSabotage(): Boolean = assignedRole?.definition?.team == Team.IMPOSTERS

    fun start() {
        val player = player
        var role = assignedRole
        if (role == null) {
            role = CrewmateRole.assignTo(this)
            assignedRole = role
        }
        addNewAbility(ReportAbility)
        role.definition.defaultAbilities.forEach { addNewAbility(it) }
        if (player != null) {
            player.sendTitlePart(
                TitlePart.TITLE,
                textComponent {
                    translatable("roles.assigned.title") {
                        args {
                            component("role", Component.translatable(role.definition.name))
                        }
                    }
                }
            )
        }

        for (player in game.players) {
            if (player === this) continue
            val p = player.player ?: continue
            mannequinController.updateNameTag(p)
        }

        statistics.onGameStart()
    }
}