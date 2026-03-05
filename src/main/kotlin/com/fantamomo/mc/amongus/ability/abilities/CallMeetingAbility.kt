package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType

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

                requiresNotInMeeting()

                requiresNoSabotage()

                requiresNotInVent()

                condition(
                    BlockReason.LimitReached,
                    Component.translatable("ability.call_meeting.call_meeting.tooltip")
                ) {
                    player.meetingButtonsPressed >= game.settings[SettingsKey.MEETING.MEETING_BUTTONS]
                }

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.BELL
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
                        itemType = ItemType.BARRIER
                        when (ctx.getBlockReason()) {
                            BlockReason.InMeeting ->
                                translationKey = "ability.call_meeting.call_meeting.already_in_meeting"

                            BlockReason.LimitReached ->
                                translationKey = "ability.call_meeting.call_meeting.button_limit_reached"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}
