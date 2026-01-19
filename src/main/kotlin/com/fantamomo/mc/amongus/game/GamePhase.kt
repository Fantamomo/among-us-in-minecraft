package com.fantamomo.mc.amongus.game

enum class GamePhase(val onDisconnectRemove: Boolean) {
    LOBBY(true),
    STARTING(true),

    RUNNING(false),
    CALLING_MEETING(false),
    DISCUSSION(false),
    VOTING(false),
    ENDING_MEETING(false),

    FINISHED(true);

    val isPlaying: Boolean
        get() = this == RUNNING || this == GamePhase.CALLING_MEETING || this == DISCUSSION || this == VOTING || this == ENDING_MEETING

    val isMeeting: Boolean
        get() = this == CALLING_MEETING || this == DISCUSSION || this == VOTING || this == ENDING_MEETING
}