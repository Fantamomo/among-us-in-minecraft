package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import org.bukkit.craftbukkit.entity.CraftZombie

@NMS
class BotController(
    val player: BotAmongUsPlayer
) {
    val entity: CraftZombie = player.game.world.spawn(player.game.area.lobbySpawn!!, org.bukkit.entity.Zombie::class.java) {
        it.isCustomNameVisible = false
        it.isInvulnerable = true
        it.isSilent = true
        it.isVisibleByDefault = false
        it.isCollidable = false
    } as CraftZombie

    val handle: Zombie = entity.handle

    init {
        handle.goalSelector.removeAllGoals { true }
        handle.targetSelector.removeAllGoals { true }
        handle.navigation.stop()

        addGoals()
    }

    private fun addGoals() {
        val goalSelector = handle.goalSelector

        goalSelector.addGoal(8, LookAtPlayerGoal(handle, Player::class.java, 8.0f))
        goalSelector.addGoal(8, RandomLookAroundGoal(handle))
        goalSelector.addGoal(8, RandomStrollGoal(handle, 1.0))
    }
}