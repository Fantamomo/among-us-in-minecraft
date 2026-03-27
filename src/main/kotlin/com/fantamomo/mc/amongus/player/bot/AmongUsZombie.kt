package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.manager.EntityManager
import com.fantamomo.mc.amongus.player.util.registerDebugValues
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.debug.DebugValueSource
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.SpawnGroupData
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftZombie
import org.bukkit.event.entity.CreatureSpawnEvent

class AmongUsZombie internal constructor(val controller: BotController, level: ServerLevel) :
    Zombie(EntityType.ZOMBIE, level) {

    init {
        val lobbySpawn = controller.player.game.area.lobbySpawn ?: throw IllegalStateException("Lobby spawn not found")
        EntityManager.addEntityToRemoveOnEnd(controller.player.game, bukkitEntity)
        isCustomNameVisible = false
        isInvulnerable = true
        isSilent = true
        bukkitEntity.isVisibleByDefault = AmongUsDebug.isEnabled(AmongUsDebug.DebugValues.BOT_SHOW_ZOMBIE)
        (bukkitEntity as CraftZombie).removeWhenFarAway = false
        collides = false
        persist = true
        setCanPickUpLoot(false)
        isBaby = false
        lobbySpawn.run { setPos(blockX.toDouble() + 0.5, blockY.toDouble(), blockZ.toDouble() + 0.5) }
        (controller.player.game.world as CraftWorld).addEntity<org.bukkit.entity.Zombie>(
            this,
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            null,
            false
        )
    }

    override fun isSunSensitive(): Boolean = false

    override fun canBreakDoors() = false

    override fun isUnderWaterConverting() = false

    override fun isPushable() = false

    override fun createNavigation(level: Level) = BotPathNavigation(this, level)

    override fun registerGoals() {}

    override fun finalizeSpawn(
        level: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        spawnReason: EntitySpawnReason,
        spawnGroupData: SpawnGroupData?
    ): SpawnGroupData? = spawnGroupData

    override fun registerDebugValues(level: ServerLevel, registrar: DebugValueSource.Registration) {
        registerDebugValues(this, registrar)
    }

    override fun getNavigation(): BotPathNavigation = navigation as BotPathNavigation
}