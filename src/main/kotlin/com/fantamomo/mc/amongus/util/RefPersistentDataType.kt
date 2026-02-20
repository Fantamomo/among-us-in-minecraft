package com.fantamomo.mc.amongus.util

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import java.lang.ref.WeakReference
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Custom [PersistentDataType] implementation that stores a weak reference
 * using a generated [Long] key as primitive representation.
 *
 * Instead of serializing the actual object, only a unique key is written
 * into the PersistentDataContainer. The real object is stored in memory
 * and accessed through that key.
 *
 * The referenced value is wrapped inside a [WeakReference] so it does not
 * prevent garbage collection.
 *
 * -----------------------------------------------------------------------
 * Developer Note (Why this exists)
 *
 * This is used inside GUI inventories of this plugin.
 *
 * In several menus, items represent runtime objects (e.g. selections,
 * configurations, or domain objects). When a player clicks an item,
 * the corresponding object needs to be resolved again.
 *
 * Instead of:
 *   - Maintaining a separate Map<ItemStack, Object>
 *   - Handling cleanup manually
 *   - Keeping additional bookkeeping structures
 *
 * The object reference is directly attached to the ItemStack via
 * the PersistentDataContainer.
 *
 * This keeps the association:
 *   - Local to the item
 *   - Simple
 *   - Self-contained
 *
 * The weak reference ensures that this mechanism does not accidentally
 * extend the lifetime of objects beyond their intended scope.
 *
 * Important:
 * - References are only valid during runtime.
 * - They are NOT meant for persistence across restarts.
 * - If the object gets garbage collected, the reference becomes unavailable.
 *
 * @param T Type of the referenced object.
 * @author Fantamomo
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalAtomicApi::class)
sealed interface RefPersistentDataType<T : Any> : PersistentDataType<Long, RefPersistentDataType.Ref<T>> {

    fun newRef(value: T): Ref<T> = Companion.newRef(value)

    sealed interface Ref<T : Any> {

        @Throws(IllegalStateException::class)
        fun get(): T

        fun getOrNull(): T?

        fun isAvailable(): Boolean
    }

    private object EmptyRef : Ref<Nothing> {
        override fun get(): Nothing = throw IllegalStateException("Empty reference")
        override fun getOrNull(): Nothing? = null
        override fun isAvailable() = false
    }

    private class RefImpl<T : Any>(val key: Long, value: T?) : Ref<T> {
        private val ref = WeakReference<T>(value)

        override fun get(): T = ref.get() ?: throw IllegalStateException("Reference is null")

        override fun getOrNull(): T? = ref.get()

        override fun isAvailable() = ref.get() != null
    }

    private object RefPersistentDataTypeImpl : RefPersistentDataType<Any> {

        override fun getPrimitiveType() = Long::class.javaObjectType

        override fun getComplexType() = Ref::class.java as Class<Ref<Any>>

        override fun toPrimitive(
            complex: Ref<Any>,
            context: PersistentDataAdapterContext
        ): Long = when (complex) {
            EmptyRef -> DatHolder.EMPTY_KEY
            is RefImpl -> complex.key
        }

        override fun fromPrimitive(
            primitive: Long,
            context: PersistentDataAdapterContext
        ) = when (primitive) {
            DatHolder.EMPTY_KEY -> EmptyRef
            else -> DatHolder.refs[primitive] ?: EmptyRef
        } as Ref<Any>
    }

    companion object {
        fun <T : Any> refPersistentDataType(): RefPersistentDataType<T> =
            RefPersistentDataTypeImpl as RefPersistentDataType<T>

        fun <T : Any> newRef(value: T): Ref<T> {
            val key = DatHolder.REF_KEY.fetchAndIncrement()
            val ref = RefImpl(key, value)
            DatHolder.refs[key] = ref
            return ref
        }
    }

    /**
     * Internal holder for reference bookkeeping.
     *
     * Maintains:
     * - A special key for empty references
     * - A monotonically increasing key generator
     * - A map of active references
     */
    private object DatHolder {
        const val EMPTY_KEY = -1L
        val REF_KEY = AtomicLong(0)
        val refs: MutableMap<Long, Ref<*>> = mutableMapOf()
    }
}