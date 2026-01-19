package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.DeactivatableAbilityItem
import com.fantamomo.mc.amongus.ability.item.game
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object CallMeetingAbility : Ability<CallMeetingAbility, CallMeetingAbility.AssignedCallMeetingAbility> {
    override val id: String = "call_meeting"

    override fun assignTo(player: AmongUsPlayer) = AssignedCallMeetingAbility(player)

    class AssignedCallMeetingAbility(override val player: AmongUsPlayer) : AssignedAbility<CallMeetingAbility, AssignedCallMeetingAbility> {
        override val definition = CallMeetingAbility
        override val items: List<AbilityItem> = listOf(CallMeetingAbilityItem(this))
    }

    @Suppress("UnstableApiUsage")
    class CallMeetingAbilityItem(ability: AssignedAbility<*, *>) : DeactivatableAbilityItem(ability, "call_meeting") {
        override fun activatedItem() = ItemStack(Material.BELL).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.call_meeting.call_meeting.active")
                }
            )
        }

        override fun deactivatedItem() = ItemStack(Material.BARRIER).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable(when {
                        game.meetingManager.isCurrentlyAMeeting() -> "ability.call_meeting.call_meeting.already_in_meeting"
                        game.sabotageManager.isCurrentlySabotage() -> "ability.call_meeting.call_meeting.sabotage"
                        ability.player.meetingButtonsPressed >= game.settings[SettingsKey.MEETING_BUTTONS] ->
                            "ability.call_meeting.call_meeting.button_limit_reached"
                        else -> "ability.call_meeting.call_meeting.error"
                    })
                }
            )
        }

        override fun canUse(): Boolean = !game.meetingManager.isCurrentlyAMeeting() &&
                !game.sabotageManager.isCurrentlySabotage() &&
                ability.player.meetingButtonsPressed < game.settings[SettingsKey.MEETING_BUTTONS]

        override fun onRightClick() {
            game.meetingManager.callMeeting(ability.player, MeetingManager.MeetingReason.BUTTON)
        }
    }
}