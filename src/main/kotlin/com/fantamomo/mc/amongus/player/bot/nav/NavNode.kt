package com.fantamomo.mc.amongus.player.bot.nav

import net.minecraft.core.BlockPos

class NavNode(
    val pos: BlockPos,
    val type: NavNodeType,
) {
    val neighbors: MutableList<NavNodeConnection> = mutableListOf()
}