package com.fantamomo.mc.amongus.util.skinblender

import java.awt.image.BufferedImage
import kotlin.math.hypot

object SkinBlender {

    private const val NOISE_STRENGTH = 0.18f
    private const val RANKEN_WIDTH = 5

    fun blendVirusWithRanken(base: BufferedImage, target: BufferedImage, t: Float): BufferedImage {
        require(base.width == target.width && base.height == target.height)

        val width = base.width
        val height = base.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val basePixels = base.getRGB(0, 0, width, height, null, 0, width)
        val targetPixels = target.getRGB(0, 0, width, height, null, 0, width)
        val resultPixels = IntArray(basePixels.size)

        val rankenCount = 3
        val rankenPaths = generateRankenPaths(width, height, rankenCount)
        val rankenOffsets = FloatArray(rankenCount) { 0.1f + 0.4f * pseudoNoise(it * 10f, 0f) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                var alpha = t + pseudoNoise(x.toFloat(), y.toFloat()) * NOISE_STRENGTH

                for ((i, path) in rankenPaths.withIndex()) {
                    val distance = distanceToPath(x.toFloat(), y.toFloat(), path)
                    if (distance < RANKEN_WIDTH) {
                        val v = y.toFloat() / height
                        if (v >= rankenOffsets[i] && v <= rankenOffsets[i] + t) {
                            val factor = 1f - (distance / RANKEN_WIDTH)
                            alpha = alpha.coerceIn(0f, 1f) + factor * (1f - alpha)
                        }
                    }
                }

                alpha = alpha.coerceIn(0f, 1f)
                resultPixels[index] = lerpColor(basePixels[index], targetPixels[index], alpha)
            }
        }

        result.setRGB(0, 0, width, height, resultPixels, 0, width)
        return result
    }

    private fun pseudoNoise(x: Float, y: Float): Float {
        var n = (x.toInt() * 374761393 + y.toInt() * 668265263)
        n = (n xor (n shr 13)) * 1274126177
        n = n xor (n shr 16)
        return (n and 0x7fffffff) / Int.MAX_VALUE.toFloat()
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val invT = 1f - t
        val aA = a ushr 24 and 0xFF
        val aR = a ushr 16 and 0xFF
        val aG = a ushr 8 and 0xFF
        val aB = a and 0xFF
        val bA = b ushr 24 and 0xFF
        val bR = b ushr 16 and 0xFF
        val bG = b ushr 8 and 0xFF
        val bB = b and 0xFF
        val rA = (aA * invT + bA * t).toInt()
        val rR = (aR * invT + bR * t).toInt()
        val rG = (aG * invT + bG * t).toInt()
        val rB = (aB * invT + bB * t).toInt()
        return (rA shl 24) or (rR shl 16) or (rG shl 8) or rB
    }

    private fun generateRankenPaths(width: Int, height: Int, count: Int): List<List<Pair<Float, Float>>> {
        val paths = mutableListOf<List<Pair<Float, Float>>>()
        for (i in 0 until count) {
            val path = mutableListOf<Pair<Float, Float>>()
            val segments = 8
            for (s in 0..segments) {
                val x = width * (s.toFloat() / segments)
                val y = height * (0.2f + 0.6f * pseudoNoise(i * 100f + s, s.toFloat()))
                path.add(x to y)
            }
            paths.add(path)
        }
        return paths
    }

    private fun distanceToPath(x: Float, y: Float, path: List<Pair<Float, Float>>): Float {
        var minDist = Float.MAX_VALUE
        for (i in 0 until path.size - 1) {
            val (x1, y1) = path[i]
            val (x2, y2) = path[i + 1]
            minDist = minOf(minDist, pointLineDistance(x, y, x1, y1, x2, y2))
        }
        return minDist
    }

    private fun pointLineDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return hypot((px - x1).toDouble(), (py - y1).toDouble()).toFloat()
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        val tClamped = t.coerceIn(0f, 1f)
        val projX = x1 + tClamped * dx
        val projY = y1 + tClamped * dy
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }
}