@file:OptIn(ExperimentalContracts::class)

package com.fantamomo.mc.amongus.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Applies the given block to the receiver object if the specified condition is true.
 *
 * @param condition A Boolean value that determines whether the block should be applied.
 * @param block A lambda block to execute on the receiver object if the condition is true.
 * @return The result of applying the block if the condition is true; otherwise, the original receiver object.
 */
inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (condition) block() else this
}

/**
 * Applies the given block to the current object if the specified condition is false.
 *
 * @param condition A Boolean value that determines whether the block should not be applied.
 * @param block A lambda function that provides an alternative value of the same type.
 * @return The current object if the condition is true; otherwise, the result of the block.
 */
inline fun <T> T.applyUnless(condition: Boolean, block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (!condition) block() else this
}