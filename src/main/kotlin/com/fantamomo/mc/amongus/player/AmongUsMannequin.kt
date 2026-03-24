package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.manager.EntityManager
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.debug.DebugValueSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.decoration.Mannequin
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.event.entity.CreatureSpawnEvent

@Suppress("UnstableApiUsage")
class AmongUsMannequin internal constructor(
    val controller: MannequinController
) : Mannequin(EntityType.MANNEQUIN, (controller.owner.game.world as CraftWorld).handle) {

    init {
        val mannequin = bukkitEntity as org.bukkit.entity.Mannequin
        EntityManager.addEntityToRemoveOnEnd(controller.owner.game, mannequin)

        persist = true
        isInvulnerable = true
        immovable = true

        val owner = controller.owner
        mannequin.profile = ResolvableProfile.resolvableProfile(owner.profile)

        var realLocation = owner.realLocation
        if (realLocation.world !== owner.game.world) {
            realLocation = owner.game.world.spawnLocation.clone()
        }
        realLocation.let { setPos(it.x, it.y, it.z) }

        (owner.game.world as CraftWorld).addEntity<org.bukkit.entity.Mannequin>(
            this,
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            null,
            false
        )
    }


    override fun registerDebugValues(level: ServerLevel, registrar: DebugValueSource.Registration) {
        if (controller.owner.isHuman) return
        if (AmongUsDebug.isEnabled(AmongUsDebug.DebugValues.BOT_SHOW_ZOMBIE)) return
        val zombie = controller.owner.controller.handle
        com.fantamomo.mc.amongus.player.util.registerDebugValues(zombie, registrar)
    }
}