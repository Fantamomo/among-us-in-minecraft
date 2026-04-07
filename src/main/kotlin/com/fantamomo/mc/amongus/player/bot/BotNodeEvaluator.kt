package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.util.internal.NMS
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.PathfindingContext
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

@NMS
class BotNodeEvaluator(
    private val auZombie: AmongUsZombie,
) : WalkNodeEvaluator() {

    override fun getPathTypeOfMob(context: PathfindingContext, x: Int, y: Int, z: Int, mob: Mob): PathType =
        if (isBlocked(x, y, z)) PathType.BLOCKED else super.getPathTypeOfMob(context, x, y, z, mob)

    override fun getPathType(context: PathfindingContext, x: Int, y: Int, z: Int): PathType =
        if (isBlocked(x, y, z)) PathType.BLOCKED else super.getPathType(context, x, y, z)

    private fun isBlocked(x: Int, y: Int, z: Int): Boolean {
        val game = auZombie.controller.player.game
        if (!game.meetingManager.isCurrentlyAMeeting()) return false
        val area = game.area
        val min = area.meetingRoomMin ?: return false
        val max = area.meetingRoomMax ?: return false
        return (min.x >= x || x >= max.x) || (min.y >= y || y >= max.y) || (min.z >= z || z >= max.z)
    }
}