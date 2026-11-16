package com.fantamomo.mc.amongus.util.coroutines

import kotlinx.coroutines.TimeoutCancellationException

private val constructor = TimeoutCancellationException::class.java.getConstructor(String::class.java).apply {
    isAccessible = true
}

fun TimeoutCancellationException(message: String) = constructor.newInstance(message) as TimeoutCancellationException