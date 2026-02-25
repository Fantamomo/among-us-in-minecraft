package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.brigadier.KtCommandBuilder
import com.fantamomo.mc.brigadier.requires
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault

object Permissions {
    private val permissions: MutableList<Permission> = mutableListOf()
    private const val PREFIX = "amongus"
    val ADMIN = perm("admin", PermissionDefault.OP)

    val AREA = perm("admin.area", PermissionDefault.OP)

    val SETTINGS = perm("admin.settings", PermissionDefault.OP)
    val SEE_GAME_CODES = perm("join.see_game_codes", PermissionDefault.OP)
    val SET_PLAYER_COLOR_OTHER = perm("admin.set_player_color_other", PermissionDefault.OP)

    val SET_PLAYER_COLOR = perm("set_player_color", PermissionDefault.TRUE)
    val SET_PLAYER_COLOR_FORCE = perm("admin.set_player_color_force", PermissionDefault.OP)
    val SET_PLAYER_TRIM = perm("set_player_trim", PermissionDefault.TRUE)
    val SET_PLAYER_TRIM_OTHER = perm("set_player_trim_other", PermissionDefault.OP)

    val PLAYER_STATS = perm("player.stats", PermissionDefault.TRUE)
    val PLAYER_LEAVE = perm("player.leave", PermissionDefault.TRUE)
    val PLAYER_JOIN = perm("player.join", PermissionDefault.TRUE)
    val PLAYER_CREATE = perm("player.create", PermissionDefault.TRUE)
    val PLAYER_SETTINGS = perm("player.settings", PermissionDefault.TRUE)

    val ADMIN_GAME_SWITCH_HOST = perm("admin.game.switch_host", PermissionDefault.OP)
    val ADMIN_GAME_INFO = perm("admin.game.info", PermissionDefault.OP)
    val ADMIN_GAME_ROLE = perm("admin.game.role", PermissionDefault.OP)
    val ADMIN_GAME_KILL = perm("admin.game.kill", PermissionDefault.OP)
    val ADMIN_GAME_LET_WIN = perm("admin.game.let_win", PermissionDefault.OP)
    val ADMIN_GAME_TASK = perm("admin.game.task", PermissionDefault.OP)
    val ADMIN_GAME_START = perm("admin.game.start", PermissionDefault.OP)
    val ADMIN_GAME_LIST = perm("admin.game.list", PermissionDefault.OP)
    val ADMIN_GAME_JOIN = perm("admin.game.join", PermissionDefault.OP)
    val ADMIN_GAME_CREATE = perm("admin.game.create", PermissionDefault.OP)

    private fun perm(
        node: String,
        default: PermissionDefault
    ) = Permission("$PREFIX.$node", default).also(permissions::add)

    @Suppress("UnstableApiUsage")
    fun registerAll() {
        AmongUs.server.pluginManager.addPermissions(permissions)
    }

    context(command: KtCommandBuilder<CommandSourceStack, *>)
    fun Permission.required() {
        command.requires { sender.hasPermission(this@required) }
    }
}
