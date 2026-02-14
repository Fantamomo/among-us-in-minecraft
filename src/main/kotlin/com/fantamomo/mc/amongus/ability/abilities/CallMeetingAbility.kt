package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.Material

object CallMeetingAbility :
    Ability<CallMeetingAbility, CallMeetingAbility.AssignedCallMeetingAbility> {

    override val id = "call_meeting"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedCallMeetingAbility(player)

    class AssignedCallMeetingAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<CallMeetingAbility, AssignedCallMeetingAbility> {

        override val definition = CallMeetingAbility

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("call_meeting") {

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    if (game.sabotageManager.isCurrentlySabotage())
                        BlockReason.Sabotage
                    else null
                }

                condition {
                    if (player.isVented())
                        BlockReason.InVent
                    else null
                }

                condition {
                    if (
                        player.meetingButtonsPressed >=
                        game.settings[SettingsKey.MEETING.MEETING_BUTTONS]
                    ) BlockReason.LimitReached else null
                }

                state(AbilityItemState.ACTIVE) {

                    render {
                        material = Material.BELL
                        translationKey = "ability.call_meeting.call_meeting.active"
                    }

                    onRightClick {
                        game.meetingManager.callMeeting(
                            player,
                            MeetingManager.MeetingReason.BUTTON
                        )
                    }
                }

                state(AbilityItemState.BLOCKED) {

                    render {
                        material = Material.BARRIER
                        translationKey = when (ctx.getBlockReason()) {
                            BlockReason.InMeeting ->
                                "ability.call_meeting.call_meeting.already_in_meeting"

                            BlockReason.Sabotage ->
                                "ability.general.disabled.sabotage"

                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.LimitReached ->
                                "ability.call_meeting.call_meeting.button_limit_reached"

                            else ->
                                "ability.call_meeting.call_meeting.error"
                        }
                    }
                }

                state(AbilityItemState.COOLDOWN) {
                    render {
                        material = Material.BARRIER
                        translationKey = "ability.general.disabled.cooldown"
                    }
                }
            }
        )
    }
}
