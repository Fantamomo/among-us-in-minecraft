package com.fantamomo.mc.amongus.area

import com.fantamomo.mc.amongus.util.SerializableLocation
import kotlinx.serialization.Serializable

@Serializable
data class GameAreaDTO(
    val name: String,
    val uuid: String,

    val minCorner: SerializableLocation?,
    val maxCorner: SerializableLocation?,
    val lobbySpawn: SerializableLocation?,
    val meetingBlock: SerializableLocation?,
    val ejectedFallPoint: SerializableLocation?,
    val ejectedViewPoint: SerializableLocation?,
    val cameraJoinPointMin: SerializableLocation?,
    val cameraJoinPointMax: SerializableLocation?,
    val lightPosMin: SerializableLocation?,
    val lightPosMax: SerializableLocation?,

    val cams: Map<String, SerializableLocation>,
    val vents: List<VentGroupDTO>,
    val lightLevers: Set<SerializableLocation>
)
