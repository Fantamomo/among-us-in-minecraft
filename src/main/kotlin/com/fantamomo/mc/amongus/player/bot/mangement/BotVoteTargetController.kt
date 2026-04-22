package com.fantamomo.mc.amongus.player.bot.mangement

import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.player.isHuman

abstract class BotVoteTargetController(val bot: BotAmongUsPlayer) {
    /**
     * This is only an indication. To avoid that smart players will find out which role a bot has, the meeting manager may choose to ignore this.
     */
    open fun executesAtTheStartOfTheMeeting(): Boolean = true

    /**
     * @return the vote target in a meeting
     */
    fun getTarget(): Target? =
        getTarget(bot.game.players.filter { it.isAlive() })

    /**
     * @param availableTargets The list of players available to vote for.
     *
     * @return the vote target in a meeting
     */
    abstract fun getTarget(availableTargets: List<AmongUsPlayer>): Target?

    /**
     * @param availableTargets The list of players available to vote for.
     * @param includeSkip If true, there will be a chance to vote for skip.
     * Note that the default action of the methode is to delegate to [getTarget] without the [includeSkip] parameter.
     *
     * @return the vote target in a meeting
     */
    open fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean = true): Target? = getTarget(availableTargets)

    class Random(bot: BotAmongUsPlayer) : BotVoteTargetController(bot) {
        override fun getTarget(availableTargets: List<AmongUsPlayer>) = getTarget(availableTargets, true)

        override fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean): Target {
            if (availableTargets.isEmpty()) return Target.Skip
            if (!includeSkip) return Target.Player(availableTargets.random())
            val skipChance = 1.0 / (availableTargets.size + 1)
            return if (kotlin.random.Random.nextDouble() < skipChance) Target.Skip else Target.Player(availableTargets.random())
        }
    }

    class VoteImitator(bot: BotAmongUsPlayer) : BotVoteTargetController(bot) {
        override fun executesAtTheStartOfTheMeeting(): Boolean = false

        override fun getTarget(availableTargets: List<AmongUsPlayer>) = getTarget(availableTargets, true)

        override fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean): Target {
            if (availableTargets.isEmpty()) return Target.Skip
            val votes = bot.game.meetingManager.meeting?.votes ?: return Target.Skip
            val voteCounts = availableTargets.associateWith { player ->
                votes.count { (it.value as? MeetingManager.Vote.For)?.target === player }
            }
            val maxVotes = voteCounts.values.maxOrNull() ?: 0
            val topCandidates = voteCounts.filter { it.value == maxVotes }.keys
            return if (topCandidates.isEmpty()) Target.Skip else Target.Player(topCandidates.random())
        }
    }

    data class AvoidOnesTeam(private val delegate: BotVoteTargetController) : BotVoteTargetController(delegate.bot) {
        override fun executesAtTheStartOfTheMeeting(): Boolean = delegate.executesAtTheStartOfTheMeeting()

        override fun getTarget(availableTargets: List<AmongUsPlayer>) = getTarget(availableTargets, true)

        override fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean): Target? {
            val team = bot.role.definition.team
            val filteredTargets = availableTargets.filterNot { it.role.definition.team == team }
            return delegate.getTarget(filteredTargets, includeSkip)
        }
    }

    data class AvoidSelf(private val delegate: BotVoteTargetController) : BotVoteTargetController(delegate.bot) {
        override fun executesAtTheStartOfTheMeeting(): Boolean = delegate.executesAtTheStartOfTheMeeting()

        override fun getTarget(availableTargets: List<AmongUsPlayer>) = getTarget(availableTargets, true)

        override fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean): Target? {
            val filteredTargets = availableTargets.filterNot { it == bot }
            return delegate.getTarget(filteredTargets, includeSkip)
        }
    }

    companion object {
        private fun checkPlayer(player: AmongUsPlayer): BotAmongUsPlayer {
            if (player.isHuman) throw IllegalArgumentException("Player is not a bot")
            return player
        }

        fun random(player: AmongUsPlayer) = Random(checkPlayer(player))
        fun voteImitator(player: AmongUsPlayer) = VoteImitator(checkPlayer(player))
        fun avoidOnesTeam(delegate: BotVoteTargetController) = AvoidOnesTeam(delegate)
        fun avoidSelf(delegate: BotVoteTargetController) = AvoidSelf(delegate)

        fun default(player: AmongUsPlayer) = avoidSelf(random(player))

        inline fun <C : BotVoteTargetController> create(block: Companion.() -> C): C = block()
    }

    sealed interface Target {
        data class Player(val player: AmongUsPlayer) : Target
        data object Skip : Target
    }
}