package com.fantamomo.mc.amongus.util

import org.bukkit.Color
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

fun toDirection(yaw: Float, pitch: Float): Triple<Float, Float, Float> {
    val yawRad = Math.toRadians(yaw.toDouble())
    val pitchRad = Math.toRadians(pitch.toDouble())
    val x = cos(pitchRad) * cos(yawRad)
    val y = cos(pitchRad) * sin(yawRad)
    val z = sin(pitchRad)
    return Triple(x.toFloat(), y.toFloat(), z.toFloat())
}

fun angleBetween(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>): Float {
    val dot = a.first * b.first + a.second * b.second + a.third * b.third
    return acos(dot.coerceIn(-1f, 1f)) * (180f / PI.toFloat())
}

fun getAimAccuracy(
    yaw: Float,
    pitch: Float,
    targetYaw: Float,
    targetPitch: Float,
    range: Float
): Float {
    val r = range.coerceIn(0f, 1f)

    val dir = toDirection(yaw, pitch)
    val targetDir = toDirection(targetYaw, targetPitch)

    val angleError = angleBetween(dir, targetDir)

    val maxAngleError = 6f + (60f * r)

    val t = (1f - angleError / maxAngleError).coerceIn(0f, 1f)

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
