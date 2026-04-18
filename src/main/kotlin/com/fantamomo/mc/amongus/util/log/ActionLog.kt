package com.fantamomo.mc.amongus.util.log

import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ActionLog(
    val id: Uuid,
    var metadata: Map<String, Any> = emptyMap()
) {
    val createdAt = Clock.System.now()
    internal val entries: ArrayDeque<ActionEntry<*>> = ArrayDeque()
    val customData: MutableMap<String, Any> = mutableMapOf()

    fun addEntry(entry: ActionEntry<*>) {
        entries.add(entry)
    }

    fun add(type: ActionElement) {
        addEntry(ActionEntry(type, Clock.System.now()))
    }

    @Suppress("UNCHECKED_CAST")
    fun <A : ActionElement> getFirst(vararg types: KClass<A>): ActionEntry<A>? =
        entries.firstOrNull { entry -> types.any { it.isInstance(entry.type) } } as? ActionEntry<A>

    @Suppress("UNCHECKED_CAST")
    fun <A : ActionElement> filter(vararg types: KClass<A>): List<ActionEntry<A>> =
        entries.filter { entry -> types.any { it.isInstance(entry.type) } } as List<ActionEntry<A>>
}