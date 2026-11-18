package com.fantamomo.mc.amongus.advancement

interface PartChangeListener {
    fun onPartAdded(parts: List<AdvancementSequencePart>, element: AdvancementSequencePart)
    fun onPartRemoved(parts: List<AdvancementSequencePart>, element: AdvancementSequencePart, exceptional: Boolean)

    companion object Empty : PartChangeListener {
        override fun onPartAdded(
            parts: List<AdvancementSequencePart>,
            element: AdvancementSequencePart
        ) {}

        override fun onPartRemoved(
            parts: List<AdvancementSequencePart>,
            element: AdvancementSequencePart,
            exceptional: Boolean
        ) {}
    }
}