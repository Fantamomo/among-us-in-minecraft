package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.manager.EntityManager
import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import java.util.*
import kotlin.time.Duration

class MannequinController(
    private val owner: AmongUsPlayer
) {

    /* =========================
       === Internal State ===
       ========================= */

    private var mannequin: Mannequin? = null
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

        player.hideEntity(AmongUs, mannequin!!)

        EntityManager.addEntityToRemoveOnStop(mannequin!!)
        lastLocation = player.location.clone()

        visibleTo.clear()

        showToAll()
    }

    fun despawn() {
        mannequin?.remove()
        mannequin = null
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
    }

    fun hideFrom(player: Player) {
        mannequin?.let {
            player.hideEntity(AmongUs, it)
            visibleTo -= player.uniqueId
        }
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

    fun unfreeze(forceSync: Boolean = true) {
        frozen = false
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
