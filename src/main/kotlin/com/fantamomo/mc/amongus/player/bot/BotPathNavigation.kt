package com.fantamomo.mc.amongus.player.bot

import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level

class BotPathNavigation(
    mob: AmongUsZombie,
    level: Level,
) : GroundPathNavigation(mob, level) {
    // todo: implement custom pathfinding functionality for vents
}