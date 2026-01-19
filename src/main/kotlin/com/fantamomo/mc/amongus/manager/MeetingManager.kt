package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.TitlePart
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.ArmorStand
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import kotlin.time.Duration

class MeetingManager(private val game: Game) : Listener {

    var meeting: Meeting? = null
        private set

    val meetingBlock: Location =
        game.area.meetingBlock ?: error("Meeting block not found")

    private val ejectionFallPoint: Location =
        game.area.ejectedFallPoint ?: error("Ejection fall point not found")

    private val ejectionViewPoint: Location =
        game.area.ejectedViewPoint ?: error("Ejection view point not found")

    private val cameraAnchor: ArmorStand =
        ejectionViewPoint.world.spawn(ejectionViewPoint, ArmorStand::class.java) {
            it.isVisible = false
            it.setCanMove(false)
            it.setGravity(false)
            it.isMarker = true
            it.isVisibleByDefault = false
            EntityManager.addEntityToRemoveOnStop(it)
        }

    private val bossBar: BossBar = BossBar.bossBar(
        Component.text(""),
        1f,
        BossBar.Color.BLUE,
        BossBar.Overlay.PROGRESS
    )

    fun tick() {
        meeting?.tick()
    }

    fun isCurrentlyAMeeting(): Boolean = meeting != null

    fun callMeeting(caller: AmongUsPlayer, reason: MeetingReason) {
        if (meeting != null) return
        meeting = Meeting(caller, reason)
    }

    inner class Meeting(
        private val caller: AmongUsPlayer,
        private val reason: MeetingReason
    ) {

        private var timer: Cooldown? = null
        private val votes: MutableMap<AmongUsPlayer, Vote> = mutableMapOf()
        private var ejectedPlayer: AmongUsPlayer? = null

        init {
            startMeeting()
        }

        private fun startMeeting() {
            setPhase(GamePhase.CALLING_MEETING)

            game.sabotageManager.currentSabotageType()
                ?.takeIf { it.isCrisis }
                ?.let { game.sabotageManager.endSabotage() }

            game.sabotageManager.currentSabotage()?.pause()

            val title = Component.translatable("meeting.called.title")
            val subtitle = textComponent {
                translatable(reason.calledTranslationKey) {
                    args { string("player", caller.name) }
                }
            }

            game.players.forEach { p ->
                if (game.cameraManager.isInCams(p)) game.cameraManager.leaveCams(p)
                if (game.ventManager.isVented(p)) game.ventManager.ventOut(p)

                p.livingEntity.teleport(meetingBlock)

                p.player?.apply {
                    closeInventory()
                    sendTitlePart(TitlePart.TITLE, title)
                    sendTitlePart(TitlePart.SUBTITLE, subtitle)
                    showBossBar(bossBar)
                }
            }

            startDiscussion()
        }

        private fun startDiscussion() {
            setPhase(GamePhase.DISCUSSION)
            timer = Cooldown(game.settings[SettingsKey.MEETING_DISCUSSION_TIME], true)
        }

        private fun startVoting() {
            setPhase(GamePhase.VOTING)
            votes.clear()
            timer = Cooldown(game.settings[SettingsKey.MEETING_VOTING_TIME], true)
            if (game.settings[SettingsKey.MEETING_DISCUSSION_TIME] >= Duration.ZERO) {
                for (player in game.players) {
                    player.player?.sendTitlePart(
                        TitlePart.TITLE,
                        textComponent {
                            translatable("meeting.voting.start")
                        }
                    )
                }
            }
        }

        private fun endVoting() {
            setPhase(GamePhase.ENDING_MEETING)
            timer = null

            ejectedPlayer = calculateVoteResult()
            showVoteResult(ejectedPlayer)

            Bukkit.getScheduler().runTaskLater(AmongUs, { ->
                startEjection(ejectedPlayer)
            }, 60L)
        }

        fun voteFor(voter: AmongUsPlayer, target: AmongUsPlayer): Boolean {
            if (game.phase != GamePhase.VOTING || voter in votes) return false
            votes[voter] = Vote.For(target)
            return true
        }

        fun voteSkip(voter: AmongUsPlayer): Boolean {
            if (game.phase != GamePhase.VOTING || voter in votes) return false
            votes[voter] = Vote.Skip
            return true
        }

        fun hasVoted(player: AmongUsPlayer): Boolean =
            player in votes

        private fun calculateVoteResult(): AmongUsPlayer? {
            val counts = mutableMapOf<AmongUsPlayer, Int>()
            var skipVotes = 0

            votes.values.forEach { vote ->
                when (vote) {
                    Vote.Skip -> skipVotes++
                    is Vote.For -> counts[vote.target] = (counts[vote.target] ?: 0) + 1
                }
            }

            val highest = counts.maxByOrNull { it.value } ?: return null
            val sameVotes = counts.values.count { it == highest.value }

            return if (sameVotes == 1) highest.key else null
        }

        private fun showVoteResult(player: AmongUsPlayer?) {
            val component = player?.let {
                textComponent {
                    translatable("meeting.result.ejected") {
                        args { string("player", it.name) }
                    }
                }
            } ?: Component.translatable("meeting.result.skip")

            game.players.forEach {
                it.player?.sendTitlePart(TitlePart.TITLE, component)
            }
        }

        private fun startEjection(player: AmongUsPlayer?) {
            if (player == null) {
                finishMeeting()
                return
            }

            val handle = (cameraAnchor as CraftEntity).handle

            game.players
                .filter { it != player }
                .forEach {
                    it.mannequinController.freeze()
                    (it.player as? CraftPlayer)?.handle?.setCamera(handle)
                }

            player.livingEntity.teleport(ejectionFallPoint)
        }

        fun onDeath(event: PlayerDeathEvent) {
            val dead = PlayerManager.getPlayer(event.player) ?: return
            if (dead != ejectedPlayer) return

            Bukkit.getScheduler().runTaskLater(AmongUs, { ->
                finishMeeting(true)
            }, 40L)
        }

        fun tick() {
            val timer = timer ?: return

            val remaining = timer.remaining().inWholeMilliseconds
            val total = timer.startDuration().inWholeMilliseconds

            when (game.phase) {
                GamePhase.DISCUSSION -> updateBossBar("meeting.bossbar.discussion", remaining, total)
                GamePhase.VOTING -> updateBossBar("meeting.bossbar.voting", remaining, total)
                else -> {}
            }

            if (!timer.isFinished()) return

            when (game.phase) {
                GamePhase.DISCUSSION -> startVoting()
                GamePhase.VOTING -> endVoting()
                else -> {}
            }
        }

        private fun updateBossBar(key: String, time: Long, total: Long) {
            bossBar.name(textComponent {
                translatable(key) {
                    args { numeric("time", (time / 1000).toInt()) }
                }
            })
            bossBar.progress(time / total.toFloat())
        }

        private fun finishMeeting(hasEjected: Boolean = false) {
            game.sabotageManager.currentSabotage()?.resume()

            game.players.forEach { p ->
                p.player?.hideBossBar(bossBar)

                if (hasEjected) {
                    p.mannequinController.unfreeze()
                    (p.player as? CraftPlayer)?.handle?.setCamera(null)
                }
            }

            meeting = null
            setPhase(GamePhase.RUNNING)
        }

        private fun setPhase(phase: GamePhase) {
            require(phase.isMeeting || phase == GamePhase.RUNNING) {
                "Invalid meeting phase: $phase"
            }
            game.phase = phase
        }
    }

    sealed interface Vote {
        object Skip : Vote
        data class For(val target: AmongUsPlayer) : Vote
    }

    enum class MeetingReason(val calledTranslationKey: String) {
        BUTTON("meeting.called.subtitle.button"),
        BODY("meeting.called.subtitle.body")
    }
}