package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.amongus.AmongUs
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault

object Permissions {
    private val permissions: MutableList<Permission> = mutableListOf()

    private const val PREFIX = "amongus"

    val ADMIN = perm("admin", PermissionDefault.OP)
    val AREA = perm("admin.area", PermissionDefault.OP)
    val SETTINGS = perm("admin.settings", PermissionDefault.OP)
    val SEE_GAME_CODES = perm("join.see_game_codes", PermissionDefault.OP)

    private fun perm(
        node: String,
        default: PermissionDefault
    ) = Permission("$PREFIX.$node", default).also(permissions::add)

    @Suppress("UnstableApiUsage")
    fun registerAll() {
        AmongUs.server.pluginManager.addPermissions(permissions)
    }
}
