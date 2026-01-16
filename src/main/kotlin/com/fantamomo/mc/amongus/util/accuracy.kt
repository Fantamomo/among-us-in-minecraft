package com.fantamomo.mc.amongus.util

import org.bukkit.Color
import kotlin.math.abs
import kotlin.math.sqrt

private fun deltaAngle(a: Float, b: Float): Float {
    var diff = (a - b) % 360f
    if (diff < -180f) diff += 360f
    if (diff > 180f) diff -= 360f
    return diff
}

fun getAimAccuracy(
    yaw: Float,
    pitch: Float,
    targetYaw: Float,
    targetPitch: Float,
    range: Float
): Float {
    val r = range.coerceIn(0f, 1f)

    val yawDiff = abs(deltaAngle(yaw, targetYaw))
    val pitchDiff = abs(pitch - targetPitch)

    // Pitch etwas stärker gewichten
    val angleError = sqrt(
        yawDiff * yawDiff +
                (pitchDiff * 1.3f) * (pitchDiff * 1.3f)
    )

    val maxAngleError = 6f + (60f * r)

    val t = (1f - angleError / maxAngleError).coerceIn(0f, 1f)

    // Smoothstep für weiche Kurve
    return t * t * (3f - 2f * t)
}

fun accuracyToColor(accuracy: Float): Color {
    val t = accuracy.coerceIn(0f, 1f)

    val r = when {
        t < 0.5f -> 1f
        else -> 2f - 2f * t
    }

    val g = when {
        t < 0.5f -> 2f * t
        else -> 1f
    }

    return Color.fromRGB(
        (255 * r).toInt(),
        (255 * g).toInt(),
        0
    )
}
