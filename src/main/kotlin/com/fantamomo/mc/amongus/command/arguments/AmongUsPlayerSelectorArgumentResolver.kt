package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.CommandSourceStack
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.advancements.criterion.MinMaxBounds
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.util.Util
import net.minecraft.world.entity.Entity
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Field
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Predicate
import kotlin.math.min
import kotlin.reflect.KClass

/*
 * This class is structurally based on the EntitySelector implementation used by
 * the Minecraft server, as provided through the paperweight development bundle
 * (Paper / paperweight-userdev). The original implementation originates from
 * Mojang Studios and is distributed in remapped form by the PaperMC project.
 *
 * PaperMC: https://github.com/PaperMC/Paper
 * paperweight-userdev: https://github.com/PaperMC/paperweight
 *
 * The original EntitySelector class is part of the net.minecraft.server (NMS)
 * codebase and © Mojang Studios.
 *
 * Purpose of this adaptation:
 * This project allows players to disconnect while still remaining part of the
 * internal game state (AmongUsPlayer). Vanilla selector resolution depends on
 * active ServerPlayer instances, which are not available for offline players.
 *
 * Therefore, this resolver mirrors and partially reimplements the selector
 * resolution logic (e.g. max results, sorting, range handling, predicates)
 * but redirects it to the custom PlayerManager / AmongUsPlayer abstraction
 * so that commands (e.g. kill, assign task, etc.) can also target players who
 * are currently offline.
 *
 * This is NOT a verbatim copy of Paper or Mojang source code. It is an adapted
 * reimplementation inspired by the original logic, modified where necessary
 * for this plugin's custom player system.
 *
 * All original Minecraft server code and related intellectual property remain
 * the property of Mojang Studios. Paper and paperweight are projects of the
 * PaperMC organization.
 */
class AmongUsPlayerSelectorArgumentResolver(
    private val handle: EntitySelector,
    private val single: Boolean = false
) {
    private val maxResults: Int = handle.maxResults
    private val includesEntities: Boolean = handle.includesEntities()
    private val worldLimited: Boolean = handle.isWorldLimited
    private val contextFreePredicates: List<Predicate<Entity>> = getField("contextFreePredicates")
    private val range: MinMaxBounds.Doubles? = getField("range")
    private val position: Function<Vec3, Vec3> = getField("position")
    private val aabb: AABB? = getField("aabb")
    private val order: BiConsumer<Vec3, List<Entity>> = getField("order")
    private val currentEntity: Boolean = handle.isSelfSelector
    private val playerName: String? = getField("playerName")
    private val entityUUID: UUID? = getField("entityUUID")
    private val type: EntityTypeTest<Entity, *> = getField("type")
    private val usesSelector: Boolean = getField("usesSelector")


    @Throws(CommandSyntaxException::class)
    private fun checkPermissions(source: net.minecraft.commands.CommandSourceStack) {
        if (!source.bypassSelectorPermissions &&
            this.usesSelector &&
            !source.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS, "minecraft.command.selector")
        ) throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create()
    }

    private fun getSinglePlayer(source: net.minecraft.commands.CommandSourceStack): AmongUsPlayer {
        this.checkPermissions(source)
        val list = this.getPlayers(source)
        if (list.size != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create()
        } else {
            return list[0]
        }
    }

    private fun getPlayers(source: net.minecraft.commands.CommandSourceStack): List<AmongUsPlayer> {
        this.checkPermissions(source)
        if (this.playerName != null) {
            return listOfNotNull(PlayerManager.getPlayer(playerName))
        } else if (this.entityUUID != null) {
            return listOfNotNull(PlayerManager.getPlayer(this.entityUUID))
        } else {
            val vec3 = this.position.apply(source.position)
            val absoluteAabb: AABB? = this.getAbsoluteAabb(vec3)
            val predicate = this.getPredicate(vec3, absoluteAabb, null)
            if (this.currentEntity) {
                val serverPlayer = source.entity
                return if (serverPlayer is ServerPlayer && predicate.test(serverPlayer))
                    listOfNotNull(PlayerManager.getPlayer(serverPlayer.bukkitEntity))
                else listOf()
            } else {
                val players = ObjectArrayList<AmongUsPlayer>()

                for (usPlayer in PlayerManager.getPlayers()) {
                    players.add(usPlayer)
                    if (players.size >= maxResults) {
                        return players
                    }
                }

                return this.sortAndLimit(vec3, players)
            }
        }
    }

    private fun getPredicate(pos: Vec3, box: AABB?, enabledFeatures: FeatureFlagSet?): Predicate<Entity> {
        val flag = enabledFeatures != null
        val flag1 = box != null
        val flag2 = this.range != null
        val i = (if (flag) 1 else 0) + (if (flag1) 1 else 0) + (if (flag2) 1 else 0)
        val list: List<Predicate<Entity>>
        if (i == 0) {
            list = this.contextFreePredicates
        } else {
            val list1: MutableList<Predicate<Entity>> = ObjectArrayList(this.contextFreePredicates.size + i)
            list1.addAll(this.contextFreePredicates)
            if (flag) {
                list1.add(Predicate { entity: Entity -> entity.type.isEnabled(enabledFeatures) })
            }

            if (flag1) {
                list1.add(Predicate { entity: Entity -> box.intersects(entity.boundingBox) })
            }

            if (flag2) {
                list1.add(Predicate { entity: Entity -> this.range.matchesSqr(entity.distanceToSqr(pos)) })
            }

            list = list1
        }

        return Util.allOf(list)
    }

    private fun sortAndLimit(pos: Vec3, entities: MutableList<AmongUsPlayer>): List<AmongUsPlayer> {
        when (order) {
            EntitySelector.ORDER_ARBITRARY -> {}
            EntitySelectorParser.ORDER_RANDOM -> entities.shuffle()
            EntitySelectorParser.ORDER_NEAREST -> entities.sortBy {
                (it.mannequinController.getEntity()?.location ?: it.locationBeforeGame).run {
                    pos.distanceToSqr(x, y, z)
                }
            }

            EntitySelectorParser.ORDER_FURTHEST -> entities.sortByDescending {
                (it.mannequinController.getEntity()?.location ?: it.locationBeforeGame).run {
                    pos.distanceToSqr(x, y, z)
                }
            }
        }

        return entities.subList(0, min(this.maxResults, entities.size))
    }

    private fun getAbsoluteAabb(pos: Vec3) = this.aabb?.move(pos)

    fun resolve(sourceStack: CommandSourceStack): List<AmongUsPlayer> {
        sourceStack as net.minecraft.commands.CommandSourceStack
        return if (single) listOf(getSinglePlayer(sourceStack))
        else getPlayers(sourceStack)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> getField(id: String): T =
        getField(id, T::class as KClass<T & Any>)

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(id: String, type: KClass<T & Any>): T {
        val field = fields.computeIfAbsent(id) {
            handle::class.java.getDeclaredField(id).apply { isAccessible = true }
        }
        return field.get(handle) as T
    }

    companion object {
        private val fields: MutableMap<String, Field> = mutableMapOf()
    }
}