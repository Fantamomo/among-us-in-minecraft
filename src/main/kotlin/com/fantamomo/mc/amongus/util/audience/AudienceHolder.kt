package com.fantamomo.mc.amongus.util.audience

import net.kyori.adventure.audience.Audience

interface AudienceHolder {
    val audience: Audience

    class ImplStatic(override val audience: Audience) : AudienceHolder

    class ImplDynamic(private val provider: () -> Audience) : AudienceHolder {
        override val audience: Audience get() = provider()
    }

    companion object {
        fun of(audience: Audience): AudienceHolder = ImplStatic(audience)
        fun of(provider: () -> Audience): AudienceHolder = ImplDynamic(provider)
    }
}