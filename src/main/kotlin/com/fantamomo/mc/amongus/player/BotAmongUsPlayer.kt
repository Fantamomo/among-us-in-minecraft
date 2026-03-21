package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.bot.BotController
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.meta.trim.ArmorTrim
import java.util.*

class BotAmongUsPlayer(
    uuid: UUID,
    game: Game,
    override val name: String,
    override val profile: PlayerProfile
) : AbstractAmongUsPlayer(uuid, game) {
    override val locale: Locale = Locale.US
    override var color: PlayerColor = game.randomPlayerColor()
    override var armorTrim: ArmorTrim? = null

    internal val hiddenPlayers: MutableSet<AmongUsPlayer> = mutableSetOf()

    val controller = BotController(this)
    override val audience: Audience = Audience.audience(controller.entity)

    override val realLocation: Location
        get() = controller.entity.location

    override fun canSee(other: AmongUsPlayer): Boolean = other !in hiddenPlayers

    override fun teleportAsync(to: Location) = controller.entity.teleportAsync(to)
    override fun teleport(to: Location) {
        controller.entity.teleport(to)
    }

    companion object {
        val profiles: List<PlayerProfile> = setOf("Notch", "Hypixel", "_jep").map { get(it) }

        private fun get(name: String) = Bukkit.createProfile(name)
    }
}