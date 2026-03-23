package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.crewmates.CrewmateRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import com.fantamomo.mc.amongus.util.RefPersistentDataType
import com.fantamomo.mc.amongus.util.audience.OptionalAudience
import com.fantamomo.mc.amongus.util.internal.Symbol
import com.fantamomo.mc.amongus.util.log.elements.AssignActionElements
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Instant
import kotlin.uuid.toKotlinUuid

class HumanAmongUsPlayer internal constructor(
    uuid: UUID,
    name: String,
    game: Game,
    val locationBeforeGame: Location
) : AbstractAmongUsPlayer(uuid, game) {
    private var _name: String = name
    private var _locale: Locale = Locale.getDefault()
    private var _profile: PlayerProfile? = null

    override val audience: OptionalAudience = OptionalAudience.of { player }

    val persistencePlayerData = PlayerDataManager.get(uuid.toKotlinUuid())

    internal var _wardrobeMannequin: Any? = NOT_SPAWNED

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
                        mannequin.equipment.helmet = color.toItemStack(armorTrim)
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

    override val name: String
        get() = player?.name?.also { _name = it } ?: _name
    var player: Player? = null
        internal set(value) {
            field = value
            if (value != null) {
                _profile = value.playerProfile
            }
        }
    override var armorTrim: ArmorTrim? = persistencePlayerData
        .let { it.trimMaterial to it.trimPattern }
        .takeIf { it.first != null && it.second != null }
        ?.let { ArmorTrim(it.first!!, it.second!!) }
        internal set(value) {
            field = value
            persistencePlayerData.trimMaterial = value?.material
            persistencePlayerData.trimPattern = value?.pattern
            updateHelmet()
        }
    override val realLocation: Location
        get() = player?.location ?: throw IllegalStateException("No location available")

    override fun canSee(other: AmongUsPlayer) = player?.canSee(other.mannequin) == true
    override fun teleportAsync(to: Location): CompletableFuture<Boolean> {
        val entity = player ?: mannequin
        return entity.teleportAsync(to)
    }

    override fun teleport(to: Location) {
        val entity = player ?: mannequin
        entity.teleport(to)
    }

    override var color: PlayerColor = persistencePlayerData.color?.takeIf { color -> game.players.none { it.color == color } }
        ?: game.randomPlayerColor()
        internal set(value) {
            field = value
            persistencePlayerData.color = value
            updateHelmet()
        }
    override val locale: Locale
        get() = player?.locale()?.also { _locale = it } ?: _locale
    override val profile: PlayerProfile
        get() = player?.playerProfile?.also { _profile = it } ?: _profile
        ?: throw IllegalStateException("No profile available")
    internal var disconnectedAt: Instant? = null
    var lastSeen: GamePhase = GamePhase.LOBBY
        internal set

    val statistics = PlayerStatistics(uuid.toKotlinUuid())
    val helpPreferences = persistencePlayerData.helpPreferences

    override fun updateHelmet() {
        val helmet = color.toItemStack(armorTrim)
        player?.inventory?.helmet = helmet
        if (_wardrobeMannequin is Mannequin) wardrobeMannequin?.equipment?.helmet = helmet
        if (!game.morphManager.isMorphed(this)) {
            mannequinController.getEntity()?.equipment?.helmet = helmet
        }
        game.updateAllWardrobeInventories()
    }

    override fun notifyAbilityItemChange(item: AbilityItem) {
        // checkGameRunning()
        val player = player ?: return
        val new = try {
            item.get()
        } catch (e: Exception) {
            game.logger.error("Failed to get ability item: ${item.id} at ${item.ability.definition.id}", e)
            game.logger.error("Please report that error to https://github.com/fantamomo/among-us-in-minecraft")
            return
        }
        val uuid = item.uuid
        val slots = player.inventory.mapIndexedNotNull { index, stack ->
            index.takeIf {
                stack?.persistentDataContainer?.get(
                    AbilityItem.ABILITY_UUID,
                    CustomPersistentDataTypes.UUID
                ) == uuid
            }
        }
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

    fun isVented() = game.ventManager.isVented(this)

    @Suppress("UNCHECKED_CAST")
    fun <A : Ability<A, S>, S : AssignedAbility<A, S>> getAssignedAbility(ability: A) =
        abilities.firstOrNull { it.definition === ability } as? S

    fun isNearVent(): Boolean = game.ventManager.isNearVent(this)

    fun isInCams(): Boolean = game.cameraManager.isInCams(this)

    fun isInGhostForm(): Boolean = game.ghostFormManager.isInGhostForm(this)

    fun isHost() = game.host === this

    override fun start() {
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
            if (modification != null) {
                game.actionLog.add(AssignActionElements.AssignModification(uuid, modification.definition.id))
                this.modification = modification
            }
        }
        modification?.onGameStart()
        modification?.onStart()
        if (player != null) {
            for (assigned in abilities) {
                for (item in assigned.items) {
                    item.startCooldown()
                    player.inventory.addItem(item.get())
                }
            }
            player.sendTitlePart(TitlePart.TIMES, Title.DEFAULT_TIMES)
            player.sendTitlePart(
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
            player.sendMessage(team.description)
        }

        for (player in game.players) {
            if (player === this || player.isBot) continue
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
        player.isInvisible = false
    }

    companion object {
        internal val WARDROBE_MANNEQUIN_OWNER = NamespacedKey(AmongUs, "wardrobe/mannequin/owner")
        private val NOT_SPAWNED = Symbol("NOT_SPAWNED")
    }
}