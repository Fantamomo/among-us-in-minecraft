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
    val gameSpawn: SerializableLocation?,
    val wardrobe: SerializableLocation?,
    val meetingRoomMin: SerializableLocation?,
    val meetingRoomMax: SerializableLocation?,
    val meetingBlock: SerializableLocation?,
    val ejectedFallPoint: SerializableLocation?,
    val ejectedViewPoint: SerializableLocation?,
    val cameraJoinPointMin: SerializableLocation?,
    val cameraJoinPointMax: SerializableLocation?,
    val lightPosMin: SerializableLocation?,
    val lightPosMax: SerializableLocation?,
    val seismicStabilizers1: SerializableLocation?,
    val seismicStabilizers2: SerializableLocation?,
    val seismicStabilizers1Particle: SerializableLocation?,
    val seismicStabilizers2Particle: SerializableLocation?,
    val communications: SerializableLocation?,
    val outgoingCommunicationBeam: SerializableLocation?,

    val cams: Map<String, SerializableLocation>,
    val vents: List<VentGroupDTO>,
    val lightLevers: Set<SerializableLocation>,
    val tasks: Map<String, Set<SerializableLocation>>
)
