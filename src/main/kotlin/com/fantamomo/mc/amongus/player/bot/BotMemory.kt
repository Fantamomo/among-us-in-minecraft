package com.fantamomo.mc.amongus.player.bot

import com.fantamomo.mc.amongus.player.BotAmongUsPlayer

class BotMemory(val bot: BotAmongUsPlayer) {
    private var id = 0
    private val memory: MutableList<Memory> = mutableListOf()

    fun getAll(): List<Memory> = memory

    fun add(value: String) = memory.add(Memory(id++, value))
    fun clear() = memory.clear()
    fun remove(id: Int) = memory.removeIf { it.id == id }

    class Memory(val id: Int, val value: String)
}