@file:OptIn(ExperimentalContracts::class)

package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.modification.modifications.TorchModification
import com.fantamomo.mc.amongus.role.Team
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// Casting, Implementation checks

val AmongUsPlayer.internal: AbstractAmongUsPlayer
    get() {
        contract { returns() implies (this@internal is AbstractAmongUsPlayer) }
        return this as AbstractAmongUsPlayer
    }

internal val AmongUsPlayer.internalEntity: LivingEntity?
    get() = when (this) {
        is BotAmongUsPlayer -> controller.entity
        is HumanAmongUsPlayer -> player
    }

val AmongUsPlayer.isHuman: Boolean
    get() {
        contract {
            returns(true) implies (this@isHuman is HumanAmongUsPlayer)
            returns(false) implies (this@isHuman is BotAmongUsPlayer)
        }
        return this is HumanAmongUsPlayer
    }

val AmongUsPlayer.human: HumanAmongUsPlayer
    get() {
        contract { returns() implies (this@human is HumanAmongUsPlayer) }
        if (!isHuman) throw IllegalStateException("Player is not a human")
        return this
    }

val AmongUsPlayer.humanOrNull: HumanAmongUsPlayer?
    get() {
        contract { returnsNotNull() implies (this@humanOrNull is HumanAmongUsPlayer) }
        return if (isHuman) human else null
    }

val AmongUsPlayer.isBot: Boolean
    get() {
        contract {
            returns(true) implies (this@isBot is BotAmongUsPlayer)
            returns(false) implies (this@isBot is HumanAmongUsPlayer)
        }
        return this is BotAmongUsPlayer
    }

val AmongUsPlayer.bot: BotAmongUsPlayer
    get() {
        contract { returns() implies (this@bot is BotAmongUsPlayer) }
        if (!isBot) throw IllegalStateException("Player is not a bot")
        return this
    }

val AmongUsPlayer.botOrNull: BotAmongUsPlayer?
    get() {
        contract { returnsNotNull() implies (this@botOrNull is BotAmongUsPlayer) }
        return if (isBot) bot else null
    }

// utils

val AmongUsPlayer.mannequin: Mannequin
    get() = mannequinController.getEntity() ?: throw IllegalStateException("Mannequin entity not available")

fun AmongUsPlayer.isAlive(): Boolean = deadReason == null

fun AmongUsPlayer.canDoTask() = role.definition.canDoTask

fun AmongUsPlayer.isInCams(): Boolean {
    contract { returns(true) implies (this@isInCams is HumanAmongUsPlayer) }
    return isHuman && game.cameraManager.isInCams(this)
}

fun AmongUsPlayer.isInGhostForm(): Boolean = game.ghostFormManager.isInGhostForm(this)

fun AmongUsPlayer.isNearVent(): Boolean = game.ventManager.isNearVent(this)

fun AmongUsPlayer.isVented(): Boolean = game.ventManager.isVented(this)

fun AmongUsPlayer.canDoTasks(): Boolean = role.definition.canDoTask

fun AmongUsPlayer.isHost() = game.host === this


fun AmongUsPlayer.canSeeWhenLightsSabotage(): Boolean =
    role.definition.team == Team.IMPOSTERS || modification?.definition === TorchModification

@OptIn(ExperimentalContracts::class)
inline fun HumanAmongUsPlayer.editStatistics(block: PlayerStatistics.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    statistics.block()
}