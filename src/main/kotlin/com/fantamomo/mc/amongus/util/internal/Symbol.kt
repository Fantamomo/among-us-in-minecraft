package com.fantamomo.mc.amongus.util.internal

/**
 * Identity-based marker object.
 *
 * This exists to provide unique runtime tokens that are compared strictly
 * by reference (===). The name is only for debugging and does not affect
 * equality. Two Symbols with the same name are still different instances.
 */
internal class Symbol(val name: String) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = name.hashCode()
    override fun toString() = "Symbol($name)"
}