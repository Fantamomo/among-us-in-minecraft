package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.KillAbility
import com.fantamomo.mc.amongus.ability.abilities.PoisonAbility
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.util.KillerRole
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.toSmartString
import net.kyori.adventure.text.Component
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

object WitchRole : Role<WitchRole, WitchRole.AssignedWitchRole>, KillerRole {
    override val id: String = "witch"
    override val team: Team = Team.IMPOSTERS

    override val defaultAbilities: Set<Ability<*, *>> =
        ImposterRole.defaultAbilities
            .filterTo(mutableSetOf()) { it != KillAbility }
            .apply { add(PoisonAbility) }

    override fun assignTo(player: AmongUsPlayer) = AssignedWitchRole(player)

    class AssignedWitchRole(override val player: AmongUsPlayer) : AssignedRole<WitchRole, AssignedWitchRole>,
        SupportBotsRole {

        private val diesAfter: Duration get() = player.game.settings[SettingsKey.ROLES.WITCH.POISON_DURATION]

        override val definition = WitchRole

        override val description: Component
            get() = textComponent {
                translatable("role.witch.description") {
                    args {
                        string("time", diesAfter.toSmartString(DurationUnit.SECONDS))
                    }
                }
            }

        var currentTarget: AmongUsPlayer? = null
            private set
        private var diesAt: Instant? = null
        private var killed = false

        fun canPoisonNewPlayer(): Boolean {
            if (currentTarget == null) return true
            val diesAt = diesAt ?: return true
            val now = Clock.System.now()
            return now > diesAt
        }

        fun setTarget(player: AmongUsPlayer) {
            this.currentTarget = player
            this.diesAt = Clock.System.now() + diesAfter
            killed = false
        }

        override fun tick(tickContext: TickContext) {
            val diesAt = diesAt ?: return
            currentTarget ?: return
            val now = Clock.System.now()
            if (now > diesAt) {
                if (killed) return
                killed = true
                player.game.killManager.killAsWitch(player)
            }
        }

        override fun scoreboardLine(): Component? {
            val currentTarget = currentTarget ?: return null
            val diesAt = diesAt ?: return null
            val now = Clock.System.now()
            val timeLeft = diesAt - now
            if (timeLeft < (-5).seconds) return null
            return textComponent {
                if (timeLeft < Duration.ZERO) {
                    translatable("role.witch.scoreboard.died") {
                        args {
                            string("player", currentTarget.name)
                        }
                    }
                } else {
                    translatable("role.witch.scoreboard.dies") {
                        args {
                            string("player", currentTarget.name)
                            string("time", timeLeft.toSmartString(DurationUnit.SECONDS))
                        }
                    }
                }
            }
        }

        override fun createBotVoteTargetController() = BotVoteTargetController.create { avoidOnesTeam(random(player)) }
    }
}