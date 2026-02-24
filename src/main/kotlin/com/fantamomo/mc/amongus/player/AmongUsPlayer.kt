package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.abilities.ReportAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.modification.modifications.TorchModification
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import com.fantamomo.mc.amongus.util.RefPersistentDataType
import com.fantamomo.mc.amongus.util.internal.Symbol
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import kotlin.time.Instant
import kotlin.uuid.toKotlinUuid

class AmongUsPlayer internal constructor(
    val uuid: UUID,
    name: String,
    val game: Game,
    val locationBeforeGame: Location
) {
    private var _name: String = name
    private var _locale: Locale = Locale.getDefault()
    private var _profile: PlayerProfile? = null
    internal val abilities: MutableList<AssignedAbility<*, *>> = mutableListOf()

    val persistencePlayerData = PlayerDataManager.get(uuid.toKotlinUuid())

    val mannequinController = MannequinController(this)

    private var _wardrobeMannequin: Any? = NOT_SPAWNED

    var wardrobeMannequin: Mannequin?
        get() = when (val m = _wardrobeMannequin) {
            is Mannequin -> m
            NOT_SPAWNED -> {
                val m = game.area.wardrobe?.takeIf { game.phase == GamePhase.LOBBY }?.let { loc ->
                    loc.world.spawn(loc, Mannequin::class.java) { mannequin ->
                        mannequin.isVisibleByDefault = false
                        mannequin.isInvulnerable = true
                        mannequin.isImmovable = true
                        @Suppress("UnstableApiUsage")
                        mannequin.profile = ResolvableProfile.resolvableProfile(profile)
                        mannequin.persistentDataContainer.set(
                            WARDROBE_MANNEQUIN_OWNER,
                            RefPersistentDataType.refPersistentDataType(),
                            RefPersistentDataType.newRef(this)
                        )
                        EntityManager.addEntityToRemoveOnEnd(game, mannequin)
                    }
                }
                _wardrobeMannequin = m
                m
            }

            else -> null
        }
        set(value) {
            _wardrobeMannequin = value
        }

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
    var armorTrim: ArmorTrim? = persistencePlayerData
        .let { it.trimMaterial to it.trimPattern }
        .takeIf { it.first != null && it.second != null }
        ?.let { ArmorTrim(it.first!!, it.second!!) }
        set(value) {
            field = value
            persistencePlayerData.trimMaterial = value?.material
            persistencePlayerData.trimPattern = value?.pattern
            updateHelmet()
        }
    var color: PlayerColor = persistencePlayerData.color?.takeIf { color -> game.players.none { it.color == color } }
        ?: game.randomPlayerColor()
        set(value) {
            field = value
            persistencePlayerData.color = value
            updateHelmet()
        }
    val visibleColor: PlayerColor
        get() {
            val camouflageTarget = game.morphManager.camouflageTarget()
            if (camouflageTarget != null) return camouflageTarget.color
            val morphPlayer = game.morphManager.getMorphedPlayer(this)
            return morphPlayer?.target?.color ?: color
        }
    val locale: Locale
        get() = player?.locale()?.also { _locale = it } ?: _locale
    val profile: PlayerProfile
        get() = player?.playerProfile?.also { _profile = it } ?: _profile
        ?: throw IllegalStateException("No profile available")
    var assignedRole: AssignedRole<*, *>? = null
        internal set
    var modification: AssignedModification<*, *>? = null
        internal set
    val tasks: MutableSet<TaskManager.RegisteredTask>
        get() = game.taskManager.get(this)
    var isAlive: Boolean = true
    var meetingButtonsPressed: Int = 0
    val canDoTasks: Boolean
        get() = assignedRole?.definition?.canDoTask != false
    internal var disconnectedAt: Instant? = null

    val statistics = PlayerStatistics(uuid.toKotlinUuid())
    val helpPreferences = persistencePlayerData.helpPreferences

    private fun checkGameRunning() {
        if (game.phase != GamePhase.RUNNING) throw IllegalStateException("Cannot perform this action in this phase")
    }

    internal fun updateHelmet() {
        val helmet = color.toItemStack(armorTrim)
        player?.inventory?.helmet = helmet
        if (_wardrobeMannequin is Mannequin) wardrobeMannequin?.equipment?.helmet = helmet
        if (!game.morphManager.isMorphed(this)) {
            mannequinController.getEntity()?.equipment?.helmet = helmet
        }
        game.updateAllWardrobeInventories()
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
        if (!game.phase.isPlaying) throw IllegalStateException("Cannot add ability in this phase")
        if (!ability.canAssignTo(this)) throw IllegalArgumentException("Ability cannot be assigned to this player")
        val assigned = ability.assignTo(this)
        AbilityManager.registerAbility(assigned)
        abilities.add(assigned)
        if (game.phase == GamePhase.LOBBY || game.phase == GamePhase.STARTING) return
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

    fun isNearVent(): Boolean = game.ventManager.isNearVent(this)

    fun isInCams(): Boolean = game.cameraManager.isInCams(this)

    fun isInGhostForm(): Boolean = game.ghostFormManager.isInGhostForm(this)

    fun canSeeWhenLightsSabotage(): Boolean = assignedRole?.definition?.team == Team.IMPOSTERS || modification?.definition === TorchModification

    fun addGhostImprovements() {
        if (isAlive) return
        val player = player ?: return
        player.addPotionEffect(GHOST_SPEED)
    }

    fun start() {
        val player = player
        var role = assignedRole
        if (role == null) {
            role = CrewmateRole.assignTo(this)
            assignedRole = role
            statistics.assignedRole[Team.CREWMATES.defaultRole]?.increment()
            statistics.assignedTeam[Team.CREWMATES]?.increment()
        }
        var modification = modification
        if (modification == null && game.settings[SettingsKey.MODIFIER.ENABLED]) {
            modification = Modification.randomModification(this)
            this.modification = modification
        }
        modification?.onGameStart()
        modification?.onStart()
        addNewAbility(ReportAbility)
        role.definition.defaultAbilities.forEach { addNewAbility(it) }
        player?.sendTitlePart(TitlePart.TIMES, Title.DEFAULT_TIMES)
        player?.sendTitlePart(
            TitlePart.TITLE,
            textComponent {
                translatable("roles.assigned.title") {
                    args {
                        component("role", role.name)
                    }
                }
            }
        )
        val team = role.definition.team
        player?.sendMessage(team.description)

        for (player in game.players) {
            if (player === this) continue
            val p = player.player ?: continue
            mannequinController.updateNameTag(p)
        }

        player?.closeInventory()
        statistics.onGameStart()
        wardrobeMannequin?.remove()
        wardrobeMannequin = null
    }

    internal fun restorePlayer() {
        val player = player ?: return
        player.removePotionEffect(PotionEffectType.SPEED)
    }

    companion object {
        internal val WARDROBE_MANNEQUIN_OWNER = NamespacedKey(AmongUs, "wardrobe/mannequin/owner")
        private val NOT_SPAWNED = Symbol("NOT_SPAWNED")
        private val GHOST_SPEED = PotionEffect(PotionEffectType.SPEED, -1, 1, false, false)
    }
}