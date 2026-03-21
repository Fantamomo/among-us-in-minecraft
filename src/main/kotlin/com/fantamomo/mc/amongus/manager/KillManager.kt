package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.crewmates.TheDamnedRole
import com.fantamomo.mc.amongus.role.neutral.CannibalRole.AssignedCannibalRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.log.elements.CustomAbilityActionElements
import com.fantamomo.mc.amongus.util.log.elements.PlayerActionElements
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Pose
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.uuid.toKotlinUuid

class KillManager(val game: Game) {
    private val corpses: MutableList<Corpse> = mutableListOf()

    @Suppress("UnstableApiUsage")
    fun showCorpse(owner: AmongUsPlayer, location: Location) {
        val mannequin = game.world.spawn(location, Mannequin::class.java) {
            it.profile = ResolvableProfile.resolvableProfile(owner.profile)
            it.pose = Pose.SLEEPING
            it.isImmovable = true
            it.persistentDataContainer.set(CORPSE_KEY, PersistentDataType.BYTE, 1)
            it.equipment.helmet = owner.color.toItemStack(owner.armorTrim)
            EntityManager.addEntityToRemoveOnEnd(game, it)
        }
        val corpse = Corpse(mannequin, owner)
        corpses.add(corpse)
    }

    fun killByImposter(imposter: AmongUsPlayer, target: AmongUsPlayer) {
        if (target.isInCams()) {
            game.cameraManager.leaveCams(target)
        }
        target.audience.run {
            sendTitlePart(
                TitlePart.TITLE,
                textComponent {
                    translatable("dead.by.imposter")
                }
            )
            val killerToShow = game.morphManager.getMorphedPlayer(imposter)?.target ?: imposter
            sendTitlePart(
                TitlePart.SUBTITLE,
                textComponent {
                    translatable("dead.by.imposter.subtitle") {
                        args {
                            component("player") {
                                objectComponent {
                                    playerHead {
                                        id(killerToShow.uuid)
                                    }
                                }
                                space()
                                text(killerToShow.name)
                            }
                        }
                    }
                }
            )
        }
        val location = target.location

        if (game.sabotageManager.isCurrentlySabotage()) {
            imposter.humanOrNull?.statistics?.killsAsImposterWhileSabotage?.increment()
            target.humanOrNull?.statistics?.killedByImposterWhileSabotage?.increment()
        }

        updateStatistics(target, imposter)

        imposter.internalEntity?.also { p ->
            val clone = location.clone()
            clone.rotation = p.location.rotation
            p.teleport(clone)
            p.addPotionEffect(slownessEffect)
        }

        showCorpse(target, location)
        target.humanOrNull?.player?.also { p ->
            p.addPotionEffect(blindnessEffect)
            p.closeInventory()
            p.sendHurtAnimation(0f)
        }
        markAsDead(target, DeadReason.Murdered(imposter))
        if (target.role.definition == TheDamnedRole) {
            game.meetingManager.callMeeting(
                imposter,
                MeetingManager.MeetingReason.BODY,
                body = target
            )
        }
        game.checkWin()
    }

    fun removeAllCorpses() {
        corpses.forEach { it.mannequin.remove() }
        corpses.clear()
    }

    fun isNearCorpse(location: Location): Boolean =
        corpses.any { it.valid && it.mannequin.location.distanceSquared(location) <= 2 * 2 }

    fun nearestCorpse(location: Location): Corpse? = corpses.minByOrNull { Double.MAX_VALUE.takeIf { _ -> !it.valid } ?: it.mannequin.location.distanceSquared(location) }?.takeIf { it.valid }

    fun canKillAsSheriff(sheriff: AmongUsPlayer): Boolean {
        val loc = sheriff.location
        val distance = game.settings[SettingsKey.KILL.KILL_DISTANCE].distance
        if (game.ventManager.isVented(sheriff)) return false
        for (player in game.players) {
            if (player === sheriff) continue
            if (!player.isAlive()) continue
            if (game.ventManager.isVented(player)) continue
            val location = player.location
            if (loc.distanceSquared(location) < distance * distance) return true
        }
        return false
    }

    fun canKillAsImposter(sheriff: AmongUsPlayer): Boolean {
        val loc = sheriff.location
        val distance = game.settings[SettingsKey.KILL.KILL_DISTANCE].distance
        for (player in game.players) {
            if (player === sheriff) continue
            if (!player.isAlive()) continue
            if (game.ventManager.isVented(player)) continue
            if (player.role.definition.team == Team.IMPOSTERS) continue
            val location = player.location
            if (loc.distanceSquared(location) < distance * distance) return true
        }
        return false
    }

    fun killNearestAsImposter(imposter: AmongUsPlayer) {
        val loc = imposter.location
        var nearest: AmongUsPlayer? = null
        var nearestDistance = Double.MAX_VALUE
        val distance = game.settings[SettingsKey.KILL.KILL_DISTANCE].distance
        for (player in game.players) {
            if (!player.isAlive()) continue
            if (game.ventManager.isVented(player)) continue
            if (player.role.definition.team == Team.IMPOSTERS) continue
            val location = player.location
            val distanceSquared = loc.distanceSquared(location)
            if (distanceSquared < nearestDistance && distanceSquared < distance * distance) {
                nearest = player
                nearestDistance = distanceSquared
            }
        }
        if (nearest != null) killByImposter(imposter, nearest)
    }

    fun kill(target: AmongUsPlayer, reason: DeadReason, corpse: Boolean = false) {
        if (target.isInCams()) {
            game.cameraManager.leaveCams(target)
        }
        if (corpse) {
            val location = target.location
            showCorpse(target, location)
        }
        markAsDead(target, reason)
        showGhosts(target)
        game.checkWin()
    }

    /**
     * Marks a player as dead and transitions them into ghost mode.
     *
     * Flow overview:
     *
     * 1. Update logical state (isAlive = false, stop death timer).
     * 2. Hide the mannequin from all currently seeing players.
     * 3. Re-show it only to players who are allowed to see ghosts.
     * 4. Make the mannequin invisible.
     * 5. Add player and relevant entities to the ghost scoreboard team.
     *
     * Why this is necessary:
     *
     * - The mannequin is set invisible so alive it will be shown transparently.
     * - The ghost team enables `canSeeFriendlyInvisibles`, allowing
     *   dead players to still see each other.
     * - We add both the real player and the mannequin entity to the
     *   ghost team to ensure consistent rendering behavior.
     *
     * This ensures:
     * - Ghosts always see each other.
     * - Visibility is handled entirely server-side without packet hacks.
     */
    private fun markAsDead(target: AmongUsPlayer, reason: DeadReason) {
        target.internal
        
        target.deadReason = reason
        target.humanOrNull?.statistics?.timeUntilDead?.timerStop()
        target.mannequinController.hideFromAll()
        target.mannequinController.showToSeeingPlayers()
        game.actionLog.add(PlayerActionElements.PlayerDeath(target.uuid.toKotlinUuid(), reason))
        val mannequin = target.mannequin
        mannequin.isInvisible = true
        showGhosts(target)
        val scoreboard = game.scoreboardManager.get(target)
        if (scoreboard != null) {
            val team = scoreboard.ghostTeam
            target.humanOrNull?.player?.let { team.addPlayer(it) }
            for (player in game.players) {
                if (player.isAlive()) continue
                val entity = player.mannequin
                entity.let(team::addEntity)
                mannequin.let { game.scoreboardManager.get(player)?.ghostTeam?.addEntity(it) }
            }
        }
        target.addGhostImprovements()
        game.audiences.forEach { it.setDirty() }
    }

    private fun showGhosts(target: AmongUsPlayer) {
        val player = target.humanOrNull?.player ?: return
        for (auPlayer in game.players) {
            if (auPlayer.isAlive() || target === auPlayer) continue
            auPlayer.mannequinController.showTo(player)
        }
    }

    internal fun onPlayerRejoin(amongUsPlayer: HumanAmongUsPlayer) {
        val scoreboard = game.scoreboardManager.get(amongUsPlayer)
        amongUsPlayer.player?.let { scoreboard?.ghostTeam?.addPlayer(it) }
    }

    fun getCorpses(): List<Corpse> = corpses.toList()

    fun killNearestAsSheriff(sheriff: AmongUsPlayer) {
        val loc = sheriff.location
        var nearest: AmongUsPlayer? = null
        var nearestDistance = Double.MAX_VALUE
        val distance = game.settings[SettingsKey.KILL.KILL_DISTANCE].distance
        for (player in game.players) {
            if (player === sheriff) continue
            if (!player.isAlive()) continue
            if (game.ventManager.isVented(player)) continue
            val location = player.location
            val distanceSquared = loc.distanceSquared(location)
            if (distanceSquared < nearestDistance && distanceSquared < distance * distance) {
                nearest = player
                nearestDistance = distanceSquared
            }
        }
        if (nearest != null) killBySheriff(sheriff, nearest)
    }

    private fun killBySheriff(
        sheriff: AmongUsPlayer,
        target: AmongUsPlayer
    ) {
        if (target.isInCams()) {
            game.cameraManager.leaveCams(target)
        }
        target.audience.run {
            sendTitlePart(
                TitlePart.TITLE,
                textComponent {
                    translatable("dead.by.sheriff")
                }
            )
            sendTitlePart(
                TitlePart.SUBTITLE,
                textComponent {
                    translatable("dead.by.sheriff.subtitle") {
                        args {
                            component("player") {
                                objectComponent {
                                    playerHead {
                                        id(sheriff.uuid)
                                    }
                                }
                                space()
                                text(sheriff.name)
                            }
                        }
                    }
                }
            )
        }
        val location = target.location

        updateStatistics(target, sheriff)

        val sheriffLoc = sheriff.location

        sheriff.internalEntity?.also { p ->
            val clone = location.clone()
            clone.rotation = p.location.rotation
            p.teleport(clone)
            p.addPotionEffect(slownessEffect)
        }

        showCorpse(target, location)
        target.humanOrNull?.player?.also { p ->
            p.addPotionEffect(blindnessEffect)
            p.closeInventory()
            p.sendHurtAnimation(0f)
        }
        markAsDead(target, DeadReason.Murdered(sheriff))

        if (!target.role.definition.team.canByKilledBySheriff) {
            target.humanOrNull?.statistics?.killedBySheriffWrong?.increment()
            sheriff.humanOrNull?.statistics?.killedAsSheriffWrong?.increment()
            showCorpse(sheriff, sheriffLoc)
            sheriff.humanOrNull?.player?.also { p ->
                p.sendTitlePart(
                    TitlePart.TITLE,
                    textComponent {
                        translatable("dead.by.sheriff.wrong") {
                            args {
                                component("player") {
                                    objectComponent {
                                        playerHead {
                                            id(target.uuid)
                                        }
                                    }
                                    space()
                                    text(target.name)
                                }
                            }
                        }
                    }
                )
                p.sendTitlePart(
                    TitlePart.SUBTITLE,
                    Component.translatable("dead.by.sheriff.subtitle.wrong")
                )
                p.addPotionEffect(blindnessEffect)
                p.sendHurtAnimation(0f)
            }
            markAsDead(sheriff, DeadReason.Suicide)
        } else {
            target.humanOrNull?.statistics?.killedBySheriffCorrect?.increment()
            sheriff.humanOrNull?.statistics?.killedAsSheriffCorrect?.increment()
        }
        game.checkWin()
    }

    private fun updateStatistics(target: AmongUsPlayer, killer: AmongUsPlayer) {
        val killerStatistics = killer.humanOrNull?.statistics
        val targetStatistics = target.humanOrNull?.statistics
        if (killerStatistics == null && targetStatistics == null) return

        killerStatistics?.kills[killer.role.definition]?.increment()
        targetStatistics?.killed[killer.role.definition]?.increment()
        targetStatistics?.timeUntilKilled?.timerStop()
        if (killer.isInGhostForm()) {
            killerStatistics?.killsInGhostForm?.increment()
            targetStatistics?.killedByPlayerInGhostForm?.increment()
        }
        if (game.morphManager.isMorphed(killer)) {
            killerStatistics?.killsWhileMorphed?.increment()
            targetStatistics?.killedByMorphedPlayer?.increment()
        }

        if (game.morphManager.isCamouflageMode()) {
            killerStatistics?.killsWhileCamouflaged?.increment()
            targetStatistics?.killedWhileCamouflaged?.increment()
        }
    }

    fun eatCorpse(player: AmongUsPlayer) {
        val cannibalRole: AssignedCannibalRole = player.role as? AssignedCannibalRole
            ?: throw IllegalStateException("Only cannibal role can eat bodies")

        val corpse = nearestCorpse(player.location) ?: return
        game.actionLog.add(CustomAbilityActionElements.CannibalEatBody(player.uuid, corpse.owner.uuid))
        corpse.remove()
        cannibalRole.incrementEatenBodies()
        game.checkWin()
    }

    class Corpse(
        val mannequin: Mannequin,
        val owner: AmongUsPlayer
    ) {
        val valid: Boolean
            get() = mannequin.isValid

        fun remove() {
            mannequin.remove()
        }
    }

    companion object {
        val CORPSE_KEY = NamespacedKey(AmongUs, "corpse")
        val slownessEffect = PotionEffect(PotionEffectType.SLOWNESS, 10, 5, false, false, false)
        val blindnessEffect = PotionEffect(PotionEffectType.BLINDNESS, 60, 5, false, false, false)
    }
}