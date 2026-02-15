package com.fantamomo.mc.amongus.util.skinblender

import com.fantamomo.mc.amongus.util.skinblender.SkinBlender.Companion.lerpColor
import java.awt.image.BufferedImage

object FadeSkinBlender : SkinBlender {
    override val id: String = "fade"

    // If you think I coded this, you’d be wrong. Credit goes to AI.
    override fun blend(base: BufferedImage, target: BufferedImage, t: Float): BufferedImage {
        require(base.width == target.width && base.height == target.height)

        val width = base.width
        val height = base.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val basePixels = base.getRGB(0, 0, width, height, null, 0, width)
        val targetPixels = target.getRGB(0, 0, width, height, null, 0, width)
        val resultPixels = IntArray(basePixels.size)

        for (i in basePixels.indices) {
            resultPixels[i] = lerpColor(basePixels[i], targetPixels[i], t)
        }

        result.setRGB(0, 0, width, height, resultPixels, 0, width)
        return result
    }
}