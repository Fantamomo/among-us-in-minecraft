package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.bot.BotController
import com.fantamomo.mc.amongus.player.bot.BotMemory
import com.fantamomo.mc.amongus.player.bot.BotName
import net.kyori.adventure.audience.Audience
import org.bukkit.Location
import org.bukkit.inventory.meta.trim.ArmorTrim
import java.util.*

/**
 * Represents a bot-controlled player in an Among Us-style game environment.
 * This class extends the abstract player type and provides specific implementations
 * and behavior for bot players.
 *
 * @param uuid The unique identifier associated with this bot player.
 * @param game The game instance to which the bot player is assigned.
 * @param botName The name and associated profile information of the bot player.
 *
 * @author Fantamomo
 * @since 2.0-SNAPSHOT
 */
class BotAmongUsPlayer internal constructor(
    uuid: UUID,
    game: Game,
    val botName: BotName
) : AbstractAmongUsPlayer(uuid, game) {
    override val name: String
        get() = botName.name
    override val profile: PlayerProfile
        get() = botName.profile
    override val locale: Locale = Locale.US
    override var color: PlayerColor = game.randomPlayerColor()
    override var armorTrim: ArmorTrim? = null

    internal val hiddenPlayers: MutableSet<AmongUsPlayer> = mutableSetOf()
    internal val memory = BotMemory(this)

    val controller = BotController(this)
    override val audience: Audience = Audience.audience(controller.entity)

    override val realLocation: Location
        get() = controller.entity.location

    override fun canSee(other: AmongUsPlayer): Boolean = super.canSee(other) && other !in hiddenPlayers

    override fun teleportAsync(to: Location) = controller.entity.teleportAsync(to)
    override fun teleport(to: Location) {
        controller.entity.teleport(to)
    }

    override fun preStart() {
        super.preStart()
        controller.preStart()
    }
}