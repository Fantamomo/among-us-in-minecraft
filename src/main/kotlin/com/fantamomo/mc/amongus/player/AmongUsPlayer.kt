package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.audience.AudienceHolder
import org.bukkit.Location
import org.bukkit.inventory.meta.trim.ArmorTrim
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Instant

/**
 * Represents a player in the game Among Us. This interface consolidates
 * player-specific attributes, capabilities, and roles, as well as their
 * interaction with the game world.
 *
 * Implementations: [HumanAmongUsPlayer] and [BotAmongUsPlayer]
 *
 * @author Fantamomo
 * @since 2.0-SNAPSHOT
 *
 * @see AbstractAmongUsPlayer
 * @see HumanAmongUsPlayer
 * @see BotAmongUsPlayer
 */
sealed interface AmongUsPlayer : AudienceHolder {
    val uuid: UUID
    val game: Game
    val name: String
    val locale: Locale
    val profile: PlayerProfile

    val color: PlayerColor
    val armorTrim: ArmorTrim?
    val visibleColor: PlayerColor

    val role: AssignedRole<*, *>
    val modification: AssignedModification<*, *>?
    val tasks: Set<TaskManager.RegisteredTask>
    val abilities: List<AssignedAbility<*, *>>

    val deadReason: DeadReason?

    val location: Location
    val realLocation: Location

    val mannequinController: MannequinController

    val meetingButtonsPressed: Int

    val lastKillTime: Instant?

    fun canSee(other: AmongUsPlayer): Boolean

    fun teleportAsync(to: Location): CompletableFuture<Boolean>

    fun teleport(to: Location)

    fun hasAbility(ability: Ability<*, *>): Boolean
}