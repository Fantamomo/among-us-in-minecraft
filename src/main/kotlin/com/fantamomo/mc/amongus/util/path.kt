package com.fantamomo.mc.amongus.util

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

@Suppress("NOTHING_TO_INLINE")
inline fun Path.safeCreateDirectories(): Path = apply {
    if (notExists()) createDirectories()
}