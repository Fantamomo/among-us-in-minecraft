package com.fantamomo.mc.amongus.util.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience

fun interface OptionalAudience : ForwardingAudience.Single {
    fun getAudience(): Audience?

    override fun audience(): Audience = getAudience() ?: Audience.empty()

    class Mutable(
        @JvmField var audience: Audience?
    ) : OptionalAudience {
        override fun getAudience() = audience
    }

    companion object {
        fun of(audience: Audience?) = Mutable(audience)

        fun of(provider: OptionalAudience): OptionalAudience = provider
    }
}