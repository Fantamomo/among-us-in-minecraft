package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.util.internal.NMS
import com.fantamomo.mc.brigadier.interception.BrigadierInterceptor
import com.fantamomo.mc.brigadier.interception.InceptionContext
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.adventure.AdventureComponent
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions
import net.minecraft.world.entity.Entity
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import java.util.function.Predicate

@Suppress("UnstableApiUsage")
object AmongUsCommands {

    private val logger = LoggerFactory.getLogger("AmongUs-MsgInterceptor")

    fun init(registrar: Commands) {
        registerAll(registrar)

        if (!AmongUsConfig.MsgCommandBlocker.disabled && !AmongUsConfig.MsgCommandBlocker.legacy) {
            if (!interceptMsgCommand(registrar)) {
                logger.warn("Failed to intercept the /msg command for private messaging.")
                logger.warn("This usually happens if another plugin overrides /msg commands.")
                logger.warn("Action: To resolve this, enable 'msg-command-blocker.legacy = true' in the config.yml.")
                logger.warn(
                    "If enabling legacy mode does not fix the issue, please report this problem to the plugin author, " +
                            "including details about other plugins that might affect private messaging commands."
                )
            }
        }
        registerEntitySelectorOption()
    }

    private fun registerAll(registrar: Commands) {
        registrar.register(AmongUs.pluginMeta, AmongUsAdminCommand, "Among Us Admin Command", listOf("aua"))
        registrar.register(AmongUs.pluginMeta, AmongUsCommand, "Among Us Command", listOf("au"))
        registrar.register(
            AmongUs.pluginMeta,
            AmongUsImposterMsgCommand,
            "Among Us Imposter Message Command",
            listOf("im")
        )
    }

    /**
     * Intercepts the Brigadier `/msg` command by replacing its execution handler.
     *
     * For more info: [brigadier-interception](https://github.com/Fantamomo/brigadier-interception)
     */
    @Suppress("UnstableApiUsage")
    private fun interceptMsgCommand(registrar: Commands): Boolean {
        logger.debug("Attempting to intercept /msg command")

        return try {
            BrigadierInterceptor.build(registrar.dispatcher) {
                interception {
                    runMsgInception()
                }
                path("msg", "targets", "message")
            }
            logger.debug("/msg command successfully intercepted")
            true
        } catch (ex: Exception) {
            logger.error("Failed to replace /msg command handler", ex)
            false
        }
    }

    /**
     * Decorator implementation for the `/msg` command.
     *
     * The interceptor:
     * - Applies game-specific messaging restrictions.
     * - Delegates to the original command when allowed.
     *
     * Rules enforced:
     * 1. Players currently in-game cannot use `/msg`.
     * 2. Players cannot message someone currently playing.
     *
     * If no rule is violated, the original command executes normally.
     *
     * If an imposter tries to message another imposter, they will receive an info message that they should use `/impostermsg`
     */
    private fun InceptionContext.runMsgInception(): Int {
        val sender = context.source.sender as? Player
            ?: return runOriginal()

        val senderAuPlayer = PlayerManager.getPlayer(sender)

        @Suppress("UNCHECKED_CAST")
        val targets = EntityArgument.getPlayers(context as CommandContext<CommandSourceStack>, "targets")

        if (targets.size == 1 && senderAuPlayer?.assignedRole?.definition?.team == Team.IMPOSTERS) {
            val target = PlayerManager.getPlayer(targets.first().bukkitEntity)
            if (target?.assignedRole?.definition?.team == Team.IMPOSTERS) {
                sender.sendMessage(Component.translatable("command.error.msg.to_imposter"))
                return 0
            }
        }

        if (senderAuPlayer != null) {
            sender.sendMessage(Component.translatable("command.error.msg.in_game"))
            return 0
        }

        val blockedTarget = targets
            .asSequence()
            .map { it.bukkitEntity }
            .firstOrNull { PlayerManager.getPlayer(it) != null }

        if (blockedTarget != null) {
            sender.sendMessage(
                textComponent {
                    translatable("command.error.msg.to_playing") {
                        args {
                            string("player", blockedTarget.name)
                        }
                    }
                }
            )
            return 0
        }

        return runOriginal()
    }

    private val ILLEGAL_GAME_CODE =
        SimpleCommandExceptionType(AdventureComponent(Component.translatable("command.utils.selector_option.game.illegal_code")))

    @NMS
    private fun registerEntitySelectorOption() {
        val clazz = EntitySelectorOptions::class.java
        val methode = clazz.getDeclaredMethod(
            "register",
            String::class.java,
            EntitySelectorOptions.Modifier::class.java,
            Predicate::class.java,
            net.minecraft.network.chat.Component::class.java
        )
        methode.isAccessible = true

        val id = "game"
        val handle: EntitySelectorOptions.Modifier = { parser ->
            parser.setSuggestions { builder: SuggestionsBuilder, _: Consumer<SuggestionsBuilder> ->
                var remaining = builder.remainingLowerCase
                var flag = true
                var flag2 = true
                if (remaining.isNotEmpty()) {
                    if (remaining.startsWith("!")) {
                        flag = false
                        remaining = remaining.substring(1)
                    } else {
                        flag2 = false
                    }
                }
                for (game in GameManager.getGames()) {
                    if (game.code.startsWith(remaining, ignoreCase = true)) {
                        if (flag2) {
                            builder.suggest("!" + game.code)
                        }

                        if (flag) {
                            builder.suggest(game.code)
                        }
                    }
                }

                builder.buildFuture()
            }
            val cursor = parser.reader.cursor
            val shouldInvertValue = parser.shouldInvertValue()
            val unquotedString = parser.reader.readUnquotedString()

            if (!Game.validCode(unquotedString)) {
                throw ILLEGAL_GAME_CODE.createWithContext(parser.reader)
            }

            parser.setIncludesEntities(false)
            parser.addPredicate { entity: Entity ->
                val bukkitEntity = entity.bukkitEntity
                if (bukkitEntity !is Player) return@addPredicate false
                val amongUsPlayer = PlayerManager.getPlayer(bukkitEntity) ?: return@addPredicate shouldInvertValue
                return@addPredicate (amongUsPlayer.game.code.equals(
                    unquotedString,
                    ignoreCase = true
                )) != shouldInvertValue
            }
        }
        val predicate: Predicate<EntitySelectorParser> = { true }
        methode.invoke(
            null,
            id,
            handle,
            predicate,
            net.minecraft.network.chat.Component.translatable("command.utils.selector_option.game")
        )
    }
}