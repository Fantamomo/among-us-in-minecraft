package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.role.Team
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
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
    private val owner: AmongUsPlayer
) {

    /* =========================
       === Internal State ===
       ========================= */

    private var mannequin: Mannequin? = null
    private var nameDisplay: TextDisplay? = null
    private var redNameDisplay: TextDisplay? = null
    private var lastLocation: Location? = null

    private val visibleTo: MutableSet<UUID> = mutableSetOf()

    private var frozen = false
    private var static = false
    private var invisible = false
    private var dontShowSneakingUntil: Long? = null

    /* =========================
       === Lifecycle ===
       ========================= */

    @Suppress("UnstableApiUsage")
    fun spawn(force: Boolean = false) {
        if (mannequin != null && !force) return

        despawn()

        val player = owner.player ?: return

        mannequin = player.world.spawn(player.location, Mannequin::class.java) {
            it.profile = ResolvableProfile.resolvableProfile(player.playerProfile)
            it.isPersistent = false
            it.isInvulnerable = true
            it.isImmovable = true
        }

        nameDisplay = player.world.spawn(player.location, TextDisplay::class.java) {
            it.text(Component.text(player.name))

            modifyTextDisplay(it)

            mannequin?.addPassenger(it)
        }
        redNameDisplay = player.world.spawn(player.location, TextDisplay::class.java) {
            it.text(Component.text(player.name, NamedTextColor.RED))
            modifyTextDisplay(it)
            it.isVisibleByDefault = false

            mannequin?.addPassenger(it)
        }

        player.hideEntity(AmongUs, mannequin!!)
        player.hideEntity(AmongUs, nameDisplay!!)
        player.hideEntity(AmongUs, redNameDisplay!!)

        EntityManager.addEntityToRemoveOnEnd(owner.game, mannequin!!)
        EntityManager.addEntityToRemoveOnEnd(owner.game, nameDisplay!!)
        EntityManager.addEntityToRemoveOnEnd(owner.game, redNameDisplay!!)
        lastLocation = player.location.clone()

        visibleTo.clear()

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
        nameDisplay?.remove()
        redNameDisplay?.remove()

        mannequin = null
        nameDisplay = null
        redNameDisplay = null

        lastLocation = null
        visibleTo.clear()
    }

    fun isSpawned(): Boolean = mannequin != null

    /* =========================
       === Visibility ===
       ========================= */

    fun showTo(player: Player) {
        if (invisible) return
        mannequin?.let {
            player.showEntity(AmongUs, it)
            visibleTo += player.uniqueId
        }

        if (PlayerManager.getPlayer(player)?.assignedRole?.definition?.team == Team.IMPOSTERS
            && owner.assignedRole?.definition?.team == Team.IMPOSTERS
        ) {
            redNameDisplay?.let { player.showEntity(AmongUs, it) }
        } else {
            nameDisplay?.let { player.showEntity(AmongUs, it) }
        }
    }

    fun updateNameTag(player: Player) {
        val amongUsPlayer = PlayerManager.getPlayer(player)

        redNameDisplay?.let { player.hideEntity(AmongUs, it) }
        nameDisplay?.let { player.hideEntity(AmongUs, it) }

        if (amongUsPlayer?.assignedRole?.definition?.team == Team.IMPOSTERS
            && owner.assignedRole?.definition?.team == Team.IMPOSTERS
        ) {
            redNameDisplay?.let { player.showEntity(AmongUs, it) }
        } else {
            nameDisplay?.let { player.showEntity(AmongUs, it) }
        }
    }

    fun hideFrom(player: Player) {
        mannequin?.let {
            player.hideEntity(AmongUs, it)
            visibleTo -= player.uniqueId
        }
        nameDisplay?.let { player.hideEntity(AmongUs, it) }
        redNameDisplay?.let { player.hideEntity(AmongUs, it) }
    }

    fun hideFromAll() {
        visibleTo.toList().forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { hideFrom(it) }
        }
    }

    fun showToAll(players: Iterable<Player>) {
        players.forEach(::showTo)
    }

    fun showToAll() {
        Bukkit.getOnlinePlayers().forEach {
            if (owner.player == it) return@forEach
            showTo(it)
        }
    }

    fun showToSeeingPlayers() {
        if (owner.isAlive) {
            showToAll()
            return
        }
        for (player in owner.game.players) {
            if (owner == player) continue
            if (player.isAlive) continue
            player.player?.let(::showTo)
        }
    }

    fun isVisibleTo(player: Player): Boolean =
        visibleTo.contains(player.uniqueId)

    fun setInvisible(value: Boolean) {
        if (invisible == value) return
        invisible = value

        if (value) hideFromAll()
    }

    /* =========================
       === Sync / Update ===
       ========================= */

    fun syncFromPlayer(force: Boolean = false) {
        if (static) return
        val player = owner.player ?: return
        val mannequin = mannequin ?: return

        val velocity = mannequin.velocity
        if (velocity.x.absoluteValue <= 0.01 && velocity.y.absoluteValue <= 0.01 && velocity.z.absoluteValue <= 0.01) {
            mannequin.isImmovable = true
        }

        if (!frozen) {
            syncLocation(player, mannequin, force)
            syncRotation(player, mannequin)
            syncPose(player, mannequin)
        }
    }

    private fun syncLocation(
        player: Player,
        mannequin: Mannequin,
        force: Boolean
    ) {
        val loc = player.location
        val last = lastLocation

        if (force || last == null || last.distanceSquared(loc) > 0.0025) {
            mannequin.teleport(loc)
            lastLocation = loc.clone()
        }
    }

    private fun syncRotation(player: Player, mannequin: Mannequin) {
        mannequin.bodyYaw = player.bodyYaw
        val location = player.location
        mannequin.setRotation(location.yaw, location.pitch)
    }

    private fun syncPose(player: Player, mannequin: Mannequin) {
        mannequin.pose = player.pose
        val dontShowSneakingUntil = dontShowSneakingUntil
        if (dontShowSneakingUntil == null || dontShowSneakingUntil <= System.currentTimeMillis()) {
            mannequin.isSneaking = player.isSneaking
            this.dontShowSneakingUntil = null
        } else {
            mannequin.isSneaking = false
        }
        mannequin.isGliding = player.isGliding
        mannequin.isJumping = player.isJumping
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

        val player = owner.player ?: return

        mannequin.isImmovable = false
        mannequin.velocity = player.velocity.clone()
    }

    fun unfreeze(forceSync: Boolean = true) {
        frozen = false
        mannequin?.isImmovable = true
        if (forceSync) syncFromPlayer(force = true)
    }

    fun setStatic(value: Boolean) {
        static = value
    }

    fun isFrozen(): Boolean = frozen
    fun isStatic(): Boolean = static

    fun showToSelf() {
        showTo(owner.player ?: return)
    }

    fun hideFromSelf() {
        hideFrom(owner.player ?: return)
    }

    fun hideSneakingFor(duration: Duration) {
        dontShowSneakingUntil = System.currentTimeMillis() + duration.inWholeMilliseconds
    }

    /* =========================
       === Utility ===
       ========================= */

    fun teleport(location: Location) {
        mannequin?.teleport(location)
        lastLocation = location.clone()
    }

    @Suppress("UnstableApiUsage")
    fun copyAppearanceFrom(player: Player) {
        mannequin?.profile =
            ResolvableProfile.resolvableProfile(player.playerProfile)
    }

    fun getEntity(): Mannequin? = mannequin
}
