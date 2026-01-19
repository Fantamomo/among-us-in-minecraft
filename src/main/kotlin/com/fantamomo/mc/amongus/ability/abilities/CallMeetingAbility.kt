package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import org.bukkit.Material

object CallMeetingAbility :
    Ability<CallMeetingAbility, CallMeetingAbility.AssignedCallMeetingAbility> {

    override val id: String = "call_meeting"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedCallMeetingAbility(player)

    class AssignedCallMeetingAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<CallMeetingAbility, AssignedCallMeetingAbility> {

        override val definition = CallMeetingAbility

        override val items: List<AbilityItem> = listOf(
            abilityItem("call_meeting") {

                material {
                    active = Material.BELL
                    inactive = Material.BARRIER
                }

                name {
                    active("ability.call_meeting.call_meeting.active")

                    inactive {
                        whenBlocked(
                            BlockReason.IN_MEETING,
                            "ability.call_meeting.call_meeting.already_in_meeting"
                        )
                        whenBlocked(
                            BlockReason.SABOTAGE,
                            "ability.general.disabled.sabotage"
                        )
                        whenBlocked(
                            BlockReason.IN_VENT,
                            "ability.general.disabled.in_vent"
                        )
                        whenBlocked(
                            BlockReason.LIMIT_REACHED,
                            "ability.call_meeting.call_meeting.button_limit_reached"
                        )
                        otherwise("ability.call_meeting.call_meeting.error")
                    }
                }

                blockWhen {
                    inMeeting()

                    sabotage()

                    inVent()

                    custom(BlockReason.LIMIT_REACHED) {
                        player.meetingButtonsPressed >=
                                game.settings[SettingsKey.MEETING_BUTTONS]
                    }
                }

                onRightClick {
                    game.meetingManager.callMeeting(
                        player,
                        MeetingManager.MeetingReason.BUTTON
                    )
                }
            }
        )
    }
}