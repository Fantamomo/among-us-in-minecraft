package com.fantamomo.mc.amongus.util

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

object CustomPersistentDataTypes {
    private abstract class CustomPersistentDataType<P, C>(primitiveType: KClass<P & Any>, customType: KClass<C & Any>) : PersistentDataType<P, C> {
        private val primitiveType = primitiveType.javaObjectType
        private val customType = customType.javaObjectType
        override fun getPrimitiveType() = primitiveType
        override fun getComplexType() = customType
    }
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
}