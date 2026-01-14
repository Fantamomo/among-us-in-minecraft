package com.fantamomo.mc.amongus.area

import com.fantamomo.mc.amongus.util.SerializableLocation
import kotlinx.serialization.Serializable

@Serializable
data class VentGroupDTO(
    val id: Int,
    val vents: List<SerializableLocation>
)
