package com.fantamomo.mc.amongus.game

enum class GamePhase(val onDisconnectRemove: Boolean) {
    /**
     * Represents the lobby phase of the game.
     *
     * Time: Unlimited
     * Next: [STARTING]
     */
    LOBBY(true),

    /**
     * Represents the starting phase of the game.
     *
     * This phase is active if the countdown to the start of the game has started.
     *
     * Time: 0 seconds - 10 seconds
     * Next: [STARTING], [REVEALING_ROLES]
     */
    STARTING(true),

    /**
     * Represents the revealing roles phase of the game.
     *
     * Time: TODO
     * Next: [RUNNING]
     */
    REVEALING_ROLES(false),

    /**
     * Represents the running phase of the game.
     *
     * Time: Unlimited
     * Next: [CALLING_MEETING], [FINISHED]
     */
    RUNNING(false),

    /**
     * Represents the phases of the meeting process.
     *
     * Time: 0 seconds
     * Next: [DISCUSSION], [VOTING]
     */
    CALLING_MEETING(false),

    /**
     * Represents the phases of the discussion process.
     *
     * Time: [com.fantamomo.mc.amongus.settings.SettingsKey.MEETING.MEETING_DISCUSSION_TIME]
     * Next: [VOTING]
     */
    DISCUSSION(false),

    /**
     * Represents the phases of the voting process.
     *
     * Time: [com.fantamomo.mc.amongus.settings.SettingsKey.MEETING.MEETING_VOTING_TIME]
     * Next: [ENDING_MEETING]
     */
    VOTING(false),

    /**
     * Represents the ending phase of the game.
     *
     * Time: 3 seconds - 13 seconds
     * Next: [RUNNING], [FINISHED]
     */
    ENDING_MEETING(false),

    /**
     * Represents the final phase of the game.
     *
     * Time: Unlimited
     */
    FINISHED(true);

    val isPlaying: Boolean
        get() = this == RUNNING || this == CALLING_MEETING || this == DISCUSSION || this == VOTING || this == ENDING_MEETING

    val isMeeting: Boolean
        get() = this == CALLING_MEETING || this == DISCUSSION || this == VOTING || this == ENDING_MEETING
}