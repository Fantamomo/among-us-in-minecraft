package com.fantamomo.mc.amongus.util

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

fun Path.safeCreateDirectories(): Path = apply {
    if (notExists()) createDirectories()
}