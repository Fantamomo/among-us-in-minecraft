package com.fantamomo.mc.amongus.util.log

import kotlin.time.Clock
import kotlin.uuid.Uuid

class ActionLog(
    val id: Uuid,
    var metadata: Map<String, Any> = emptyMap()
) {
    val createdAt = Clock.System.now()
    internal val entries: ArrayDeque<ActionEntry> = ArrayDeque()

    fun addEntry(entry: ActionEntry) {
        entries.add(entry)
    }

    fun add(type: ActionElement) {
        addEntry(ActionEntry(type, Clock.System.now()))
    }
}