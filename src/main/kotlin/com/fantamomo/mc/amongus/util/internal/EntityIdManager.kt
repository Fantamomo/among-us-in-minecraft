package com.fantamomo.mc.amongus.util.internal

import java.util.*
import java.util.concurrent.atomic.AtomicInteger

object EntityIdManager {

    private const val MIN_ID = 200_000_000
    private const val MAX_ID = 2_000_000_000

    private val nextId = AtomicInteger(MIN_ID)

    private val recycledIds = PriorityQueue<Int>()

    @Synchronized
    fun getFreeId(): Int {
        recycledIds.poll()?.let { return it }

        val id = nextId.getAndIncrement()
        if (id > MAX_ID) {
            throw IllegalStateException("No free entity id available")
        }
        return id
    }

    fun getFreeIds(count: Int): List<Int> =
        List(count) { getFreeId() }

    @Synchronized
    fun freeId(id: Int) {
        if (id in MIN_ID until nextId.get()) {
            recycledIds.add(id)
        }
    }
}