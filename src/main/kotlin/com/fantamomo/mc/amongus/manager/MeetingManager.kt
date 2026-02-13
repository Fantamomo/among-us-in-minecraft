package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.content
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.LanguageManager
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.Cooldown
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.TitlePart
import net.kyori.adventure.util.TriState
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.ArmorStand
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MenuType.STONECUTTER
import org.bukkit.inventory.StonecuttingRecipe
import org.bukkit.inventory.view.StonecutterView
import org.bukkit.persistence.PersistentDataType
import kotlin.time.Duration
import kotlin.time.DurationUnit

class MeetingManager(private val game: Game) : Listener {

    var meeting: Meeting? = null
        private set

    val meetingBlock: Location =
        game.area.meetingBlock ?: error("Meeting block not found")

    private val ejectionFallPoint: Location =
        game.area.ejectedFallPoint ?: error("Ejection fall point not found")

    private val ejectionViewPoint: Location =
        game.area.ejectedViewPoint ?: error("Ejection view point not found")

    private val buttonCooldown = Cooldown(game.settings[SettingsKey.MEETING_BUTTON_COOLDOWN], true)

    private val actionBar = game.actionBarManager.ActionBarPart(
        "meeting",
        ActionBarManager.ActionBarPartType.CENTER,
        500,
        false,
        null
    ).apply {
        componentProvider = {
            val meeting = meeting
            if (meeting == null) null
            else textComponent {
                when (game.phase) {
                    GamePhase.CALLING_MEETING -> translatable("meeting.actionbar.calling")
                    GamePhase.DISCUSSION -> translatable("meeting.actionbar.discussing")
                    GamePhase.VOTING -> translatable("meeting.actionbar.voting")
                    GamePhase.ENDING_MEETING -> translatable("meeting.actionbar.ending")
                    else -> content("<error>")
                }
            }
        }
    }

    internal val cameraAnchor: ArmorStand =
        ejectionViewPoint.world.spawn(ejectionViewPoint, ArmorStand::class.java) {
            it.isVisible = false
            it.setCanMove(false)
            it.setGravity(false)
            it.isMarker = true
            it.isVisibleByDefault = false
            EntityManager.addEntityToRemoveOnEnd(game, it)
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

    fun callMeeting(
        caller: AmongUsPlayer,
        reason: MeetingReason,
        force: Boolean = reason == MeetingReason.BODY,
        updateStatistics: Boolean = true
    ) {
        if (meeting != null) return
        if (!caller.isAlive) return
        if (force) {
            meeting = Meeting(caller, reason)
            return
        }
        game.taskManager.updateBossbar(meeting = true)
        if (game.sabotageManager.isCurrentlySabotage()) {
            caller.player?.sendMessage(Component.translatable("meeting.sabotage_in_progress"))
            return
        }
        if (buttonCooldown.isRunning()) {
            caller.player?.sendMessage(textComponent {
                translatable("meeting.button_cooldown") {
                    args { string("time", buttonCooldown.remaining().toString(DurationUnit.SECONDS, 0)) }
                }
            })
            return
        }
        if (caller.meetingButtonsPressed >= game.settings[SettingsKey.MEETING_BUTTONS]) {
            caller.player?.sendMessage(Component.translatable("meeting.button_limit_reached"))
            return
        }
        caller.meetingButtonsPressed++
        buttonCooldown.reset()
        meeting = Meeting(caller, reason)
        if (updateStatistics) {
            val statistics = caller.statistics
            statistics.calledEmergency.increment()
            when (reason) {
                MeetingReason.BUTTON -> statistics.buttonPressed.increment()
                MeetingReason.BODY -> statistics.foundBodies.increment()
            }
        }
        game.invalidateAbilities()
    }

    @Suppress("UnstableApiUsage")
    inner class Meeting(
        private val caller: AmongUsPlayer,
        private val reason: MeetingReason
    ) {
        private var timer: Cooldown? = null
        private val votes: MutableMap<AmongUsPlayer, Vote> = mutableMapOf()
        var respawnLocation: Location? = null
            private set
        var ejectedPlayer: AmongUsPlayer? = null
            private set
        val recipes: MutableMap<NamespacedKey, StonecuttingRecipe> = mutableMapOf()
        val voteInventories: MutableMap<AmongUsPlayer, StonecutterView> = mutableMapOf()
        var currentlyEjecting: Boolean = false
            private set
        var disableEventHandler: Boolean = false
            private set

        init {
            registerRecipes()
            startMeeting()
        }

        private fun registerRecipes() {
            val server = AmongUs.server
            for (amongUsPlayer in game.players) {
                if (!amongUsPlayer.isAlive) continue
                val player = amongUsPlayer.player ?: continue
                val key = NamespacedKey(AmongUs, "meeting/voting/${amongUsPlayer.uuid}")
                recipeKeys.add(key)

                val recipe = server.getRecipe(key)
                if (recipe != null) {
                    recipes[key] = recipe as StonecuttingRecipe
                    continue
                }

                val result = ItemStack(Material.PLAYER_HEAD).apply {
                    setData(
                        DataComponentTypes.PROFILE,
                        ResolvableProfile.resolvableProfile(player.playerProfile)
                    )
                    val value = textComponent(LanguageManager.ROOT_LOCALE) {
                        translatable("meeting.voting.vote_for") {
                            args {
                                string("player", amongUsPlayer.name)
                            }
                        }
                    }
                    setData(
                        DataComponentTypes.CUSTOM_NAME,
                        value.decoration(TextDecoration.ITALIC, false)
                    )
                    editPersistentDataContainer {
                        it.set(VOTING_KEY, PersistentDataType.STRING, amongUsPlayer.uuid.toString())
                    }
                }

                val stonecutter = StonecuttingRecipe(key, result, Material.BARRIER)
                server.addRecipe(stonecutter)
                recipes[key] = stonecutter
            }
            val key = NamespacedKey(AmongUs, "meeting/voting/skip")
            recipeKeys.add(key)
            val recipe = server.getRecipe(key)
            if (recipe != null) {
                recipes[key] = recipe as StonecuttingRecipe
                return
            }
            val result = ItemStack(Material.STRUCTURE_VOID).apply {
                setData(
                    DataComponentTypes.ITEM_NAME,
                    textComponent(LanguageManager.ROOT_LOCALE) {
                        translatable("meeting.voting.vote_skip")
                    }
                )
                editPersistentDataContainer {
                    it.set(VOTING_KEY, PersistentDataType.STRING, "skip")
                }
            }
            val stonecutter = StonecuttingRecipe(key, result, Material.BARRIER)
            server.addRecipe(stonecutter)
            recipes[key] = stonecutter
        }

        private fun startMeeting() {
            setPhase(GamePhase.CALLING_MEETING)

            game.sabotageManager.currentSabotageType()
                ?.takeIf { it.isCrisis }
                ?.let { game.sabotageManager.endSabotage() }

            game.sabotageManager.currentSabotage()?.pause()

            game.killManager.removeAllCorpses()

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

                game.actionBarManager.bar(p).add(actionBar)
            }

            startDiscussion()
        }

        private fun startDiscussion() {
            setPhase(GamePhase.DISCUSSION)
            val duration = game.settings[SettingsKey.MEETING_DISCUSSION_TIME]
            if (duration <= Duration.ZERO) {
                startVoting()
                return
            }
            timer = Cooldown(duration, true)
        }

        private fun startVoting() {
            for (amongUsPlayer in game.players) {
                val player = amongUsPlayer.player ?: continue
                for (key in recipes.keys) {
                    player.discoverRecipe(key)
                }
            }
            setPhase(GamePhase.VOTING)
            votes.clear()
            timer = Cooldown(game.settings[SettingsKey.MEETING_VOTING_TIME], true)
            if (game.settings[SettingsKey.MEETING_DISCUSSION_TIME] >= Duration.ZERO) {
                game.sendTitle(
                    TitlePart.TITLE,
                    textComponent {
                        translatable("meeting.voting.start")
                    }
                )
                game.sendTitle(
                    TitlePart.SUBTITLE,
                    textComponent {
                        translatable("meeting.voting.start.subtitle")
                    }
                )
            }
        }

        private fun endVoting() {
            setPhase(GamePhase.ENDING_MEETING)
            timer = null

            ejectedPlayer = calculateVoteResult()
            respawnLocation = ejectedPlayer?.livingEntity?.location
            showVoteResult(ejectedPlayer)

            votes.entries.forEach { (player, vote) ->
                player.statistics.voted.increment()
                when (vote) {
                    is Vote.For -> {
                        val target = vote.target
                        target.statistics.accused.increment()
                        if (target.assignedRole?.definition?.team == Team.IMPOSTERS) {
                            player.statistics.votedCorrect.increment()
                            target.statistics.accusedCorrect.increment()
                        } else {
                            player.statistics.votedWrong.increment()
                            target.statistics.accusedWrong.increment()
                        }
                    }

                    Vote.Skip -> player.statistics.votedSkip.increment()
                }
            }

            for (player in game.players) {
                player.player?.closeInventory()
            }

            Bukkit.getScheduler().runTaskLater(AmongUs, { ->
                startEjection(ejectedPlayer)
            }, 60L)
        }

        fun voteFor(voter: AmongUsPlayer, target: AmongUsPlayer): Boolean {
            if (game.phase != GamePhase.VOTING || voter in votes) return false
            if (!target.isAlive) return false
            votes[voter] = Vote.For(target)
            mayEndVoting()
            return true
        }

        private fun mayEndVoting() {
            val end = game.players.all { !it.isAlive || hasVoted(it) }
            if (end) {
                endVoting()
            }
        }

        fun voteSkip(voter: AmongUsPlayer): Boolean {
            if (game.phase != GamePhase.VOTING || voter in votes) return false
            votes[voter] = Vote.Skip
            mayEndVoting()
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
            if (highest.value <= skipVotes) return null
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

            game.sendTitle(TitlePart.TITLE, component)
        }

        private fun startEjection(player: AmongUsPlayer?) {
            if (player == null || !player.isAlive) {
                finishMeeting()
                return
            }

            val handle = (cameraAnchor as CraftEntity).handle

            game.players
                .filter { it != player }
                .forEach {
                    it.player?.visualFire = TriState.FALSE
                    it.mannequinController.freeze()
                    it.player?.showEntity(AmongUs, cameraAnchor)
                    (it.player as? CraftPlayer)?.handle?.setCamera(handle)
                }

            player.livingEntity.teleport(ejectionFallPoint)
            currentlyEjecting = true

            val statistics = player.statistics

            statistics.ejected.increment()

            if (player.assignedRole?.definition?.team == Team.IMPOSTERS) {
                statistics.ejectedCorrect.increment()
            } else {
                statistics.ejectedWrong.increment()
            }

            Bukkit.getScheduler().runTaskLater(AmongUs, { ->
                if (currentlyEjecting) finishMeeting(true)
            }, 200L)
        }

        fun onDeath(event: PlayerDeathEvent) {
            val dead = PlayerManager.getPlayer(event.player) ?: return
            if (dead != ejectedPlayer) return

            event.showDeathMessages = false
            event.isCancelled = true
            val player = dead.player
            if (player != null) {
                player.health = 20.0
                player.fireTicks = 0
                player.visualFire = TriState.FALSE
                respawnLocation?.let { player.teleport(it) }
            }

            Bukkit.getScheduler().runTaskLater(AmongUs, { ->
                if (currentlyEjecting) finishMeeting(true)
            }, 40L)
        }

        fun openVoteInventory(player: AmongUsPlayer) {
            if (hasVoted(player)) {
                voteInventories.remove(player)
                return
            }
            val view = STONECUTTER.builder()
                .title(Component.translatable("meeting.voting.title"))
                .build(player.player ?: return)

            val item = ItemStack(Material.BARRIER).apply {
                setData(
                    DataComponentTypes.ITEM_NAME,
                    textComponent(player.locale) {
                        translatable("meeting.voting.close")
                    }
                )
                editPersistentDataContainer {
                    it.set(VOTING_KEY, PersistentDataType.STRING, "close")
                }
            }
            voteInventories[player] = view
            view.topInventory.setItem(0, item)
            view.open()
            view.topInventory.setItem(0, item)
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

            currentlyEjecting = false

            disableEventHandler = true
            game.players.forEach { p ->

                if (hasEjected && p != ejectedPlayer) {
                    (p.player as? CraftPlayer)?.handle?.setCamera(null)
                    p.player?.hideEntity(AmongUs, cameraAnchor)
                    p.mannequinController.getEntity()?.let { p.player?.teleport(it.location) }
                    p.mannequinController.unfreeze()
                }
                val player = p.player
                if (player != null) {
                    player.hideBossBar(bossBar)
                    player.fireTicks = 0
                    player.visualFire = TriState.NOT_SET

//                    for (key in recipes.keys) {
//                        player.undiscoverRecipe(key)
//                    }
                }

                actionBar.remove()
            }
            disableEventHandler = false

            for (key in recipes.keys) {
                Bukkit.removeRecipe(key)
            }

            Bukkit.updateRecipes()

            ejectedPlayer?.let { player ->
                player.statistics.timeUntilDead.timerStop()
                player.statistics.timeUntilVotedOut.timerStop()
                respawnLocation?.let { loc -> player.player?.teleport(loc) }
                player.mannequinController.getEntity()?.let { mannequin ->
                    mannequin.fireTicks = 0
                }
                game.killManager.kill(player, false)
            }

            recipes.clear()
            voteInventories.clear()

            meeting = null
            buttonCooldown.start()
            setPhase(GamePhase.RUNNING)
            game.invalidateAbilities()
        }

        private fun setPhase(phase: GamePhase) {
            require(phase.isMeeting || phase == GamePhase.RUNNING) {
                "Invalid meeting phase: $phase"
            }
            game.phase = phase
        }
    }

    companion object {
        private val recipeKeys: MutableSet<NamespacedKey> = mutableSetOf()

        internal fun dispose() {
            recipeKeys.forEach { Bukkit.removeRecipe(it) }
        }

        val VOTING_KEY = NamespacedKey(AmongUs, "meeting/voting")
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