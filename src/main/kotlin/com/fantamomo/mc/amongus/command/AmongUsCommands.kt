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
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import io.papermc.paper.adventure.AdventureComponent
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.ShadowBrigNode
import net.kyori.adventure.text.Component
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions
import net.minecraft.world.entity.Entity
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.util.function.Consumer
import java.util.function.Predicate

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
     * Why not use PlayerCommandPreprocessEvent?
     *
     * 1. Preprocess events only provide the raw command string.
     * 2. There is no reliable way to ensure it is actually the Brigadier `/msg` node.
     * 3. Commands executed through `/execute` would bypass such interception.
     * 4. String-based interception is fragile and error-prone.
     *
     * This method:
     * - Locates the root `/msg` node.
     * - Navigates to `targets -> message`.
     * - Replaces the execution handler using reflection.
     *
     * Important:
     * It is not necessary to intercept aliases such as `/tell` or `/w`.
     *
     * In vanilla/Paper, these aliases internally redirect to the same Brigadier
     * command node as `/msg`. Since we replace the execution handler directly
     * on the `/msg` Brigadier node, all aliases automatically inherit the
     * modified behavior.
     *
     * This avoids:
     * - Duplicate interception logic
     * - Multiple reflection modifications
     * - Inconsistent alias behavior
     *
     * As long as aliases resolve to the same Brigadier node,
     * this interception remains fully centralized and consistent.
     *
     * @return true if interception succeeds.
     */
    @NMS
    @Suppress("UnstableApiUsage")
    private fun interceptMsgCommand(registrar: Commands): Boolean {
        logger.debug("Attempting to intercept /msg command")

        val dispatcher = registrar.dispatcher
        val rootMsgNode = dispatcher.root.children
            .firstOrNull { it.name == "msg" } as? ShadowBrigNode
            ?: return logAndReturn("Could not find /msg command node")

        val targetsNode = rootMsgNode.handle.children
            .firstOrNull { it.name == "targets" }
            ?: return logAndReturn("Could not find 'targets' node in /msg")

        val messageNode = targetsNode.children
            .firstOrNull { it.name == "message" } as? ArgumentCommandNode<*, *>
            ?: return logAndReturn("Could not find 'message' node in /msg")

        val commandField = getCommandField() ?: return false

        @Suppress("UNCHECKED_CAST")
        val oldCommand = messageNode.command as Command<CommandSourceStack>

        return try {
            commandField.set(messageNode, MsgInterceptor(oldCommand))
            logger.debug("/msg command successfully intercepted")
            true
        } catch (ex: Exception) {
            logger.error("Failed to replace /msg command handler", ex)
            false
        }
    }

    private fun getCommandField(): Field? {
        return try {
            CommandNode::class.java.getDeclaredField("command").apply {
                isAccessible = true
            }
        } catch (ex: Exception) {
            logger.error("Failed to access Brigadier command field via reflection", ex)
            null
        }
    }

    private fun logAndReturn(message: String): Boolean {
        logger.warn(message)
        return false
    }

    /**
     * Decorator implementation for the `/msg` command.
     *
     * Pattern Used: Command Decorator
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
    @NMS
    private class MsgInterceptor(
        private val original: Command<CommandSourceStack>
    ) : Command<CommandSourceStack> {
        override fun run(ctx: CommandContext<CommandSourceStack>): Int {
            val sender = ctx.source.sender as? Player
                ?: return original.run(ctx)

            val senderAuPlayer = PlayerManager.getPlayer(sender)
            val targets = EntityArgument.getPlayers(ctx, "targets")

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

            return original.run(ctx)
        }
    }

    private val ILLEGAL_GAME_CODE = SimpleCommandExceptionType(AdventureComponent(Component.translatable("command.utils.selector_option.game.illegal_code")))

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