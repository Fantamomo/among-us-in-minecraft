package com.fantamomo.mc.amongus.util

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

object CustomPersistentDataTypes {
    private abstract class CustomPersistentDataType<P, C>(primitiveType: KClass<P & Any>, customType: KClass<C & Any>) : PersistentDataType<P, C> {
        private val primitiveType = primitiveType.javaObjectType
        private val customType = customType.javaObjectType
        override fun getPrimitiveType() = primitiveType
        override fun getComplexType() = customType
    }

    /**
     * A custom persistent data type for UUIDs.
     *
     * It stores kotlin [Uuid]s as byte arrays.
     */
    val UUID: PersistentDataType<ByteArray, Uuid> = object : CustomPersistentDataType<ByteArray, Uuid>(ByteArray::class, Uuid::class) {
        override fun toPrimitive(
            complex: Uuid,
            context: PersistentDataAdapterContext
        ) = complex.toByteArray()

        override fun fromPrimitive(
            primitive: ByteArray,
            context: PersistentDataAdapterContext
        ) = Uuid.fromByteArray(primitive)
    }

    private class EnumPersistentDataType<E : Enum<E>>(enumClass: KClass<E>, val entries: EnumEntries<E>) : CustomPersistentDataType<Int, E>(Int::class, enumClass) {
        override fun toPrimitive(complex: E, context: PersistentDataAdapterContext) = complex.ordinal

        override fun fromPrimitive(primitive: Int, context: PersistentDataAdapterContext): E = entries[primitive]
    }

    @PublishedApi
    internal fun <E : Enum<E>> enum(enumClass: KClass<E>, enumEntries: EnumEntries<E>): PersistentDataType<Int, E> = EnumPersistentDataType(enumClass, enumEntries)

    inline fun <reified E : Enum<E>> enum(): PersistentDataType<Int, E> = enum(E::class, enumEntries())
}