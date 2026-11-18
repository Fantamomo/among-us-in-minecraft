package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.imposters.MorphlingRole

@OptIn(ExperimentalAdvancementApi::class)
object IdentityTheftAdvancement : SequencedAdvancement("identity_theft") {
    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition === MorphlingRole

    override val runner = createRunner {
        val morphStart = step("morph") {
            val ownPlayer = awaitOwnPlayer { awaitMorphStart() }
            if (ownPlayer.morphedPlayer.target.role.definition.team === Team.IMPOSTERS) stepFailed()
            ownPlayer
        }

        val morphTarget = morphStart.morphedPlayer.target

        step("kill_in_disguise") {
            awaitOwnPlayer { awaitKill() }
            if (!morphStart.morphedPlayer.isValid()) stepFailed()
        }
        step("frame_job") {
            val meetingEnd = awaitMeetingEnd()
            if (meetingEnd.ejectedPlayer !== morphTarget) stepFailed()
        }

        award()
    }
}