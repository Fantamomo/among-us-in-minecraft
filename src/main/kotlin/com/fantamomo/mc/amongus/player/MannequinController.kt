package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.ProfileProperty
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.modification.modifications.LaggyModification
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.crewmates.SnitchRole
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.util.Transformation
import java.util.*
import kotlin.math.absoluteValue
import kotlin.time.Duration

/**
 * Controls a Mannequin entity that visually replaces the real player entity
 * for other clients.
 *
 * =========================
 * === Design Rationale ===
 * =========================
 *
 * We intentionally do NOT show the real Player entity to other players.
 * Instead, we:
 *
 * 1. Hide the real player using Paper's hideEntity/showEntity system.
 * 2. Spawn a Mannequin that mirrors the player's state.
 * 3. Fully control what other players are allowed to see.
 *
 * The primary reason for this architecture is full visual control.
 *
 * Example problem:
 * If we wanted to hide the item in the player's main hand without using a
 * Mannequin, we would need to:
 *
 * - Send equipment update packets with AIR to all other players
 *   after every main-hand switch.
 *
 * This introduces a race-condition problem:
 * Other players may briefly see the real item before the override packet arrives.
 *
 * Alternative approach (tested):
 * - Intercept outgoing packets using ProtocolLib and modify/cancel them.
 *
 * Why this was rejected:
 * - It requires intercepting and maintaining many different packet types.
 * - Complex edge cases appear (e.g. when a player enters cameras).
 * - In camera mode, essentially ALL player-related packets would need to be blocked.
 * - The solution becomes fragile, hard to maintain, and error-prone.
 *
 * Additional benefit:
 * By fully replacing the visual representation, we can also control how
 * name tags appear to specific players.
 *
 * Example:
 * Other imposters can see their teammates' names in red,
 * while normal players see the default name in white.
 *
 * This kind of per-viewer name customization would be significantly harder
 * (and less reliable) when using the real Player entity.
 *
 * Final decision:
 * Use Paper's hideEntity/showEntity system and replace the player visually
 * with a Mannequin.
 *
 * Advantages:
 * - No packet-level hacks.
 * - No race conditions.
 * - No visual flicker.
 * - No need to block dozens of packet types.
 * - Complete server-side control over:
 *      - Equipment visibility
 *      - Name tag rendering (including per-player color differences)
 *      - Pose & rotation
 *      - Sneaking state
 *      - Visibility per player
 *
 * In short:
 * Instead of fighting the client synchronization model,
 * we replace the visual representation entirely.
 *
 * @author Fantamomo
 * @since 1.0-SNAPSHOT
 */
class MannequinController(
    val owner: AmongUsPlayer
) {

    /* =========================
       === Internal State ===
       ========================= */

    var handle: AmongUsMannequin? = null
        private set
    private var mannequin: Mannequin? = null
    private var lastLocation: Location? = null

    private val colorDisplays: MutableMap<TextColor, TextDisplay> = mutableMapOf()
    private val viewerColors: MutableMap<UUID, TextColor> = mutableMapOf()

    private val hiddenFrom: MutableSet<UUID> = mutableSetOf()

    private var frozen = false
    private var static = false
    private var invisible = false
    private var dontShowSomePosesUntil: Long? = null

    /* =========================
       === Lifecycle ===
       ========================= */

    fun spawn(force: Boolean = false) {
        if (mannequin != null && !force) return

        despawn()

        val player = owner.humanOrNull?.player

        var realLocation = owner.realLocation
        if (realLocation.world !== owner.game.world) {
            realLocation = owner.game.world.spawnLocation.clone()
        }
        val handle = AmongUsMannequin(this)
        this.handle = handle

        mannequin = handle.bukkitEntity as Mannequin

        player?.hideEntity(AmongUs, mannequin!!)

        EntityManager.addEntityToRemoveOnEnd(owner.game, mannequin!!)

        lastLocation = realLocation

        hiddenFrom.clear()
        viewerColors.clear()
        colorDisplays.clear()

        showToAll()
    }

    private fun modifyTextDisplay(display: TextDisplay) {
        display.isPersistent = false
        display.isInvulnerable = true
        display.isSeeThrough = false
        display.viewRange = 12.5f
        display.billboard = Display.Billboard.CENTER

        val t = display.transformation
        display.transformation =
            Transformation(t.translation.add(0f, 0.2f, 0f), t.leftRotation, t.scale, t.rightRotation)
    }

    fun despawn() {
        mannequin?.remove()

        colorDisplays.values.forEach { it.remove() }

        mannequin = null
        colorDisplays.clear()
        viewerColors.clear()

        lastLocation = null
        hiddenFrom.clear()
    }

    fun isSpawned(): Boolean = mannequin != null

    /* =========================
       === Visibility ===
       ========================= */

    fun showTo(player: Player) {
        if (invisible) return
        mannequin?.let {
            player.showEntity(AmongUs, it)
            hiddenFrom -= player.uniqueId
        }

        updateNameTag(player, true)
    }

    fun updateNameTag(player: AmongUsPlayer, force: Boolean = false) {
        updateNameTag(player.humanOrNull?.player ?: return, force)
    }

    fun updateNameTag(player: Player, force: Boolean = false) {
        val mannequin = mannequin ?: return
        if (!player.canSee(mannequin)) return

        val amongUsPlayer = PlayerManager.getPlayer(player)

        val color = viewerColors[player.uniqueId]
            ?: determineDefaultColorFor(player, amongUsPlayer)

        if (!force && colorDisplays[color]?.let { player.canSee(it) } == true) {
            return
        }

        colorDisplays.values.forEach {
            player.hideEntity(AmongUs, it)
        }

        val display = colorDisplays.getOrPut(color) {
            val morphManager = owner.game.morphManager
            val nameToDisplay =
                (morphManager.camouflageTarget() ?: morphManager.getMorphedPlayer(owner)?.target ?: owner).name

            mannequin.world.spawn(mannequin.location, TextDisplay::class.java) {
                it.text(Component.text(nameToDisplay, color))
                modifyTextDisplay(it)
                it.isVisibleByDefault = color == NamedTextColor.WHITE
                mannequin.addPassenger(it)
                EntityManager.addEntityToRemoveOnEnd(owner.game, it)
                if (it.isVisibleByDefault) owner.humanOrNull?.player?.hideEntity(AmongUs, it)
            }
        }

        player.showEntity(AmongUs, display)
        viewerColors[player.uniqueId] = color
    }

    fun setNameColorFor(viewer: AmongUsPlayer, color: TextColor?) {
        setNameColorFor0(viewer.uuid, color)
        updateNameTag(viewer)
    }

    fun setNameColorFor(viewer: Player, color: TextColor?) {
        setNameColorFor0(viewer.uniqueId, color)
        updateNameTag(viewer)
    }

    fun setNameColorFor(viewer: UUID, color: TextColor?) {
        setNameColorFor0(viewer, color)
        updateNameTag(Bukkit.getPlayer(viewer) ?: return)
    }

    private fun setNameColorFor0(viewer: UUID, color: TextColor?) {
        if (color == null) {
            viewerColors.remove(viewer)
        } else {
            viewerColors[viewer] = color
        }
    }

    fun resetNameColorFor(viewer: UUID) {
        viewerColors.remove(viewer)
    }

    private fun determineDefaultColorFor(
        viewer: Player,
        viewerAmongUs: AmongUsPlayer?
    ): TextColor {

        val assignedRole = viewerAmongUs?.internal?.assignedRole

        return if (
            owner.internal.assignedRole?.definition?.team == Team.IMPOSTERS &&
            (assignedRole?.definition?.team == Team.IMPOSTERS ||
                    (assignedRole as? SnitchRole.AssignedSnitchRole)?.canSeeImposters() == true)
        ) {
            NamedTextColor.RED
        } else {
            NamedTextColor.WHITE
        }
    }

    fun hideFrom(player: Player) {
        mannequin?.let {
            player.hideEntity(AmongUs, it)
            hiddenFrom += player.uniqueId
        }

        val textColor = viewerColors[player.uniqueId]
        textColor?.let { color ->
            colorDisplays[color]?.let {
                player.hideEntity(AmongUs, it)
            }
        }
        if (textColor != NamedTextColor.WHITE) {
            colorDisplays[NamedTextColor.WHITE]?.let {
                player.hideEntity(AmongUs, it)
            }
        }

        viewerColors.remove(player.uniqueId)
    }

    fun hideFromAll() {
        Bukkit.getOnlinePlayers().forEach { hideFrom(it) }
    }

    fun showToAll(players: Iterable<Player>) {
        players.forEach(::showTo)
    }

    fun showToAll() {
        Bukkit.getOnlinePlayers().forEach {
            if (owner.humanOrNull?.player == it) return@forEach
            showTo(it)
        }
    }

    fun showToSeeingPlayers() {
        if (owner.isAlive()) {
            showToAll()
            return
        }
        for (player in owner.game.players) {
            if (owner === player) continue
            if (player.isAlive() || player.isBot) continue
            player.player?.let(::showTo)
        }
    }

    fun isVisibleTo(player: Player): Boolean =
        !hiddenFrom.contains(player.uniqueId)

    fun setInvisible(value: Boolean) {
        if (invisible == value) return
        invisible = value

        if (value) hideFromAll()
    }

    /* =========================
       === Sync / Update ===
       ========================= */

    fun syncFromOwner(force: Boolean = false) {
        if (static) return
        val entity = owner.internalEntity ?: return
        val mannequin = mannequin ?: return

        val velocity = mannequin.velocity
        if (velocity.x.absoluteValue <= 0.01 && velocity.y.absoluteValue <= 0.01 && velocity.z.absoluteValue <= 0.01) {
            mannequin.isImmovable = true
        }

        if (!frozen) {
            val modification = owner.modification
            if (modification !is LaggyModification.AssignedLaggyModification || modification.shouldSync()) {
                syncLocation(entity, mannequin, force)
                syncRotation(entity, mannequin)
                syncPose(entity, mannequin)
                syncAttributes(entity, mannequin)
            }
        }
    }

    private fun syncAttributes(entity: LivingEntity, mannequin: Mannequin) {
        SYNC_ATTRIBUTES.forEach { attribute ->
            val instance = entity.getAttribute(attribute)
            if (instance != null) {
                var mannequinAttribute = mannequin.getAttribute(attribute)
                if (mannequinAttribute == null) {
                    mannequin.registerAttribute(attribute)
                    mannequinAttribute = mannequin.getAttribute(attribute)
                }
                mannequinAttribute?.baseValue = instance.value
            }
        }
    }

    private fun syncLocation(
        entity: LivingEntity,
        mannequin: Mannequin,
        force: Boolean
    ) {
        val loc = entity.location
        val last = lastLocation

        if (force || last == null || last.world !== loc.world || last.distanceSquared(loc) > 0.0025) {
            mannequin.teleport(loc)
            lastLocation = loc.clone()
        }
    }

    private fun syncRotation(entity: LivingEntity, mannequin: Mannequin) {
        mannequin.bodyYaw = entity.bodyYaw
        val location = entity.location
        mannequin.setRotation(location.yaw, location.pitch)
    }

    private fun syncPose(entity: LivingEntity, mannequin: Mannequin) {
        val pose = entity.pose
        val dontShowSomePosesUntil = dontShowSomePosesUntil
        if (dontShowSomePosesUntil == null || dontShowSomePosesUntil <= System.currentTimeMillis()) {
            mannequin.pose = pose
            mannequin.isSneaking = entity.isSneaking
            this.dontShowSomePosesUntil = null
        } else {
            if (pose != Pose.SNEAKING && pose != Pose.CROAKING) mannequin.pose = pose
            mannequin.isSneaking = false
        }
        mannequin.isGliding = entity.isGliding
        mannequin.isJumping = entity.isJumping
    }

    /* =========================
       === Modes ===
       ========================= */

    fun freeze() {
        frozen = true
    }

    fun freezeWithPhysics() {
        frozen = true

        val mannequin = mannequin ?: return

        val livingEntity = owner.internalEntity ?: return

        mannequin.isImmovable = false
        mannequin.velocity = livingEntity.velocity.clone()
    }

    fun unfreeze(forceSync: Boolean = true) {
        frozen = false
        mannequin?.isImmovable = true
        if (forceSync) syncFromOwner(force = true)
    }

    fun setStatic(value: Boolean) {
        static = value
    }

    fun isFrozen(): Boolean = frozen
    fun isStatic(): Boolean = static

    fun showToSelf() {
        if (owner.isBot) return
        showTo(owner.player ?: return)
    }

    fun hideFromSelf() {
        if (owner.isBot) return
        hideFrom(owner.player ?: return)
    }

    fun hideSomePosesFor(duration: Duration) {
        dontShowSomePosesUntil = System.currentTimeMillis() + duration.inWholeMilliseconds
    }

    /* =========================
       === Utility ===
       ========================= */

    fun teleport(location: Location) {
        mannequin?.teleport(location)
        lastLocation = location.clone()
    }

    fun restoreAppearance() {
        copyAppearanceFrom(owner)
    }

    fun restoreAppearanceFromOriginalOrMorph() {
        val target = owner.game.morphManager.getMorphedPlayer(owner)?.target ?: owner
        copyAppearanceFrom(target)
    }

    @Suppress("UnstableApiUsage")
    fun copyAppearanceFrom(player: AmongUsPlayer) {
        mannequin?.profile =
            ResolvableProfile.resolvableProfile(player.profile)
        val helmet = player.color.toItemStack(player.armorTrim)
        mannequin?.equipment?.helmet = helmet
        for ((color, display) in colorDisplays) {
            display.text(Component.text(player.name, color))
        }
    }

    @Suppress("UnstableApiUsage")
    fun setSkinTexture(textureBase64: String, signature: String? = null) {
        val mannequin = mannequin ?: return

        val uuid = UUID.randomUUID()
        val profile = Bukkit.createProfileExact(uuid, uuid.toString().take(16))

        profile.setProperty(
            ProfileProperty(
                "textures",
                textureBase64,
                signature
            )
        )

        mannequin.profile = ResolvableProfile.resolvableProfile(profile)
    }

    fun getEntity(): Mannequin? = mannequin

    companion object {
        private val SYNC_ATTRIBUTES = setOf(
            Attribute.SCALE
        )
    }
}
