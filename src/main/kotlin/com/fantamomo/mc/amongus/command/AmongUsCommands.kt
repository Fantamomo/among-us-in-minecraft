package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.role.Team
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.ShadowBrigNode
import net.kyori.adventure.text.Component
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.lang.reflect.Field

object AmongUsCommands {

    private val logger = LoggerFactory.getLogger("AmongUs-MsgInterceptor")

    fun init(registrar: Commands) {
        registerAll(registrar)

        if (!interceptMsgCommand(registrar)) {
            logger.warn("Failed to intercept /msg command. It may have been overridden by another plugin.")
        }
    }

    private fun registerAll(registrar: Commands) {
        registrar.register(AmongUs.pluginMeta, AmongUsAdminCommand, "Among Us Admin Command", listOf("aua"))
        registrar.register(AmongUs.pluginMeta, AmongUsCommand, "Among Us Command", listOf("au"))
        registrar.register(AmongUs.pluginMeta, AmongUsImposterMsgCommand, "Among Us Imposter Message Command", listOf("im"))
    }

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
}