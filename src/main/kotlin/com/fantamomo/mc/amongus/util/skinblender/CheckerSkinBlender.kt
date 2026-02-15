package com.fantamomo.mc.amongus.util.skinblender

import java.awt.image.BufferedImage

object CheckerSkinBlender : SkinBlender {
    override val id: String = "checker"

    override fun blend(base: BufferedImage, target: BufferedImage, t: Float): BufferedImage {
        require(base.width == target.width && base.height == target.height)

        val width = base.width
        val height = base.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val basePixels = base.getRGB(0, 0, width, height, null, 0, width)
        val targetPixels = target.getRGB(0, 0, width, height, null, 0, width)
        val resultPixels = IntArray(basePixels.size)

        val checkerSize = 8
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val useTarget = ((x / checkerSize) + (y / checkerSize)) % 2 == 0
                resultPixels[index] = if (useTarget) targetPixels[index] else basePixels[index]
            }
        }

        result.setRGB(0, 0, width, height, resultPixels, 0, width)
        return result
    }
}