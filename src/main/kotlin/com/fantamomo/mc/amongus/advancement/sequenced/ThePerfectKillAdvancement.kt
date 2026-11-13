package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.internalEntity
import com.fantamomo.mc.amongus.role.Team
import kotlin.time.Duration.Companion.seconds

/**
 * The sequence of events for the perfect kill:
 * 1. Kill someone, but with two witnesses
 * 2. one witness reports the body within 15 seconds
 * 3. The killer survives the following meeting
 */
@OptIn(ExperimentalAdvancementApi::class)
object ThePerfectKillAdvancement : SequencedAdvancement("the_perfect_kill") {

    private const val WITNESS_DISTANCE = 16.0

    override fun applicableTo(player: HumanAmongUsPlayer) =
        player.role.definition.team == Team.IMPOSTERS

    override val runner = createRunner {
        val killEvent = awaitOwnPlayer { awaitKill() }
        val victim = killEvent.victim

        val victimEntity = victim.internalEntity ?: return@createRunner

        // find all players who could have witnessed the kill
        val witnesses = player.game.players.filter { candidate ->

            if (candidate == player || candidate == victim) {
                return@filter false
            }

            val entity = candidate.internalEntity ?: return@filter false

            // Check distance between witness and victim
            if (entity.world != victimEntity.world) return@filter false

            if (entity.location.distanceSquared(victimEntity.location) > WITNESS_DISTANCE * WITNESS_DISTANCE) {
                return@filter false
            }

            // Check whether the witness has a direct line of sight
            entity.hasLineOfSight(victimEntity)
        }

        // at least two witnesses are required
        if (witnesses.size < 2) return@createRunner

        // a witness must report the body within 15 seconds
        val meetingCall = withTimeout(15.seconds) { awaitMeetingCall() }

        if (meetingCall.player !in witnesses) return@createRunner

        // the killer must survive the meeting
        val meetingEnd = awaitMeetingEnd()

        if (meetingEnd.ejectedPlayer == player) return@createRunner

        award()
    }
}