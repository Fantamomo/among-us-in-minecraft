package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.advancement.utils.step.stepFailed
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.internalEntity
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.inventory.ItemType
import kotlin.time.Duration.Companion.seconds

/**
 * The sequence of events for the perfect kill:
 * 1. Kill someone, but with two witnesses
 * 2. one witness reports the body within 15 seconds
 * 3. The killer survives the following meeting
 */
@OptIn(ExperimentalAdvancementApi::class)
object ThePerfectKillAdvancement : SequencedAdvancement("the_perfect_kill") {

    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.IRON_SWORD

    private const val WITNESS_DISTANCE = 16.0

    override fun applicableTo(player: HumanAmongUsPlayer) =
        player.role.definition.team == Team.IMPOSTERS

    override val runner = createRunner {
        val killEvent = step("kill") {
            awaitOwnPlayer { awaitKill() }
        }
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
        step("at_least_two_witnesses") {
            val enoughWitnesses = witnesses.size >= 2
            if (!enoughWitnesses) stepFailed()
        }

        // a witness must report the body within 15 seconds
        step("witness_meeting_call") {
            val meetingCall = withTimeout(15.seconds) { awaitMeetingCall() }
            if (meetingCall.player !in witnesses) stepFailed()
        }

        // the killer must survive the meeting
        step("survive_meeting") {
            val meetingEnd = awaitMeetingEnd()
            if (meetingEnd.ejectedPlayer == player) stepFailed()
        }

        award()
    }
}