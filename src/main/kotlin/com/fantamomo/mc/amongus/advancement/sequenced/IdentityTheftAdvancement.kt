package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.events.roles.MorphStartEvent
import com.fantamomo.mc.amongus.events.roles.MorphStopEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.imposters.MorphlingRole
import org.bukkit.inventory.ItemType

/**
 * Morph into a crewmate, kill another player while morphed, and get the player you morphed into ejected in the next meeting.
 */
@OptIn(ExperimentalAdvancementApi::class)
object IdentityTheftAdvancement : SequencedAdvancement("identity_theft") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.PLAYER_HEAD

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition === MorphlingRole

    override val runner = createRunner {
        val morphTarget = step("morph") {
            awaitOwn<MorphStartEvent> { it.morphedPlayer.target.role.definition.team !== Team.IMPOSTERS }
        }.morphedPlayer.target

        step("kill_in_disguise") {
            // the morph must still be active when the kill happens
            forbidOwn(MorphStopEvent::class) {
                awaitOwnKill { it.victim != morphTarget }
            }
        }
        step("frame_job") {
            val meetingEnd = awaitMeetingEnd()
            if (meetingEnd.ejectedPlayer !== morphTarget) stepFailed()
        }
    }
}
