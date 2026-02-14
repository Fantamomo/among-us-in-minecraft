package com.fantamomo.mc.amongus.util.internal

/**
 * Annotation to indicate that a function interacts with NMS (net.minecraft.server) classes or
 * classes outside the public Paper API.
 *
 * This annotation is used to flag interactions with internal or non-public APIs to facilitate
 * easier tracking of breaking changes during updates.
 *
 * Apply this annotation to functions that directly use NMS-related components or APIs not
 * guaranteed to remain stable across versions.
 *
 * @author Fantamomo
 * @since 1.0-SNAPSHOT
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class NMS