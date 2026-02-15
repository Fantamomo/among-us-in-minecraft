package com.fantamomo.mc.amongus.util.skinblender

import java.awt.image.BufferedImage

interface SkinBlender {

    val id: String

    fun blend(base: BufferedImage, target: BufferedImage, t: Float): BufferedImage

    companion object {
        val blenders: List<SkinBlender> = listOf(VirusSkinBlender)

        fun lerpColor(a: Int, b: Int, t: Float): Int {
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
    }
}