package com.fantamomo.mc.amongus.util.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience

sealed interface ListAudience : ForwardingAudience {
    override fun audiences(): Iterable<Audience>

    fun setDirty()

    class Impl(private val provider: () -> Collection<Audience>) : ListAudience {
        private var cachedAudiences: Collection<Audience>? = null

        override fun audiences() = cachedAudiences ?: provider().also { cachedAudiences = it }

        override fun setDirty() {
            cachedAudiences = null
        }
    }

    companion object {
        fun audienceHolder(provider: () -> Collection<AudienceHolder>): ListAudience = Impl { provider().map { it.audience } }

        fun audience(provider: () -> Collection<Audience>): ListAudience = Impl(provider)
    }
}