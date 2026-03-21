package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.humanOrNull
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.util.internal.NMS
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.*
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f

class RoleRevealManager(val game: Game) {

    internal var viewEntity: ArmorStand? = null
        private set

    internal var ignorePlayerStopSpectating = false
        private set

    private val playerEntities: MutableMap<AmongUsPlayer, PlayerRevealEntities> = mutableMapOf()

    private val playerAbilityIndex: MutableMap<AmongUsPlayer, Int> = mutableMapOf()

    fun start() {
        val lobbySpawn = game.area.lobbySpawn
            ?: throw IllegalStateException("Lobby spawn is not set")

        val forward = lobbySpawn.direction.normalize()

        val cameraPos = lobbySpawn.clone().apply {
            add(forward.clone().multiply(CAMERA_DISTANCE))
            y = lobbySpawn.y + CAMERA_EYE_HEIGHT
            yaw = lobbySpawn.yaw + 180f
            pitch = CAMERA_PITCH
        }

        val viewEntity = spawnViewEntity(cameraPos)
        this.viewEntity = viewEntity

        for (player in game.players) {
            player.mannequinController.hideFromAll()
            if (player.isBot) continue
            val p = player.player ?: continue
            p.isInvisible = true
            p.teleportAsync(cameraPos)
            p.showEntity(AmongUs, viewEntity)
            p.setCamera(viewEntity)

            val entities = spawnRevealEntities(lobbySpawn, forward, player)
            playerEntities[player] = entities
            entities.showTo(p)
            playerAbilityIndex[player] = 0
            entities.abilityEntries.firstOrNull()?.let { applyHighlight(it, highlighted = true) }
            updateAbilityDescription(player)
        }

        AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
            finish()
        }, REVEAL_DURATION_TICKS)
    }

    internal fun onPlayerRejoin(player: HumanAmongUsPlayer) {
        if (game.phase != GamePhase.REVEALING_ROLES) return
        val p = player.player ?: return
        val viewEntity = viewEntity ?: return

        p.isInvisible = true
        p.teleportAsync(viewEntity.location)
        p.showEntity(AmongUs, viewEntity)

        for (other in game.players) {
            if (other == player) continue
            other.mannequinController.hideFrom(p)
        }

        playerEntities[player]?.showTo(p)

        AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
            if (game.phase != GamePhase.REVEALING_ROLES) return@runTaskLater
            p.setCamera(viewEntity)
        }, 3L)
    }

    private fun spawnViewEntity(location: Location): ArmorStand =
        game.world.spawn(location, ArmorStand::class.java) {
            it.isVisible = false
            it.isMarker = true
            it.setCanMove(false)
            it.setGravity(false)
            it.isVisibleByDefault = false
            EntityManager.addEntityToRemoveOnEnd(game, it)
        }

    private fun spawnRevealEntities(
        base: Location,
        forward: Vector,
        player: HumanAmongUsPlayer
    ): PlayerRevealEntities {
        val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()

        val mannequin = game.world.spawn(base, Mannequin::class.java) {
            it.setGravity(false)
            it.isVisibleByDefault = false
            it.isInvulnerable = true
            it.isImmovable = true
            it.equipment.helmet = player.color.toItemStack(player.armorTrim)
            @Suppress("UnstableApiUsage")
            it.profile = ResolvableProfile.resolvableProfile(player.profile)
            EntityManager.addEntityToRemoveOnEnd(game, it)
        }

        val nameDisplay = spawnTextDisplay(
            base.clone().apply { y += NAME_HEIGHT },
            buildNameText(player),
            scale = SCALE_NAME
        )

        val roleDisplay = spawnTextDisplay(
            base.clone().apply {
                add(right.clone().multiply(TEXT_LEFT_OFFSET))
                y += ROLE_HEIGHT
            },
            buildRoleText(player),
            scale = SCALE_BODY
        )

        val abilityDescriptionDisplay = spawnTextDisplay(
            base.clone().apply {
                add(right.clone().multiply(TEXT_LEFT_OFFSET))
                y += ABILITY_DESCRIPTION_HEIGHT
            },
            Component.empty(),
            scale = SCALE_BODY,
        )

        val abilities: List<AssignedAbility<*, *>> = player.abilities
        val items: List<AbilityItem> = abilities.flatMap { it.items }

        val abilityEntities = items.mapIndexed { index, abilityItem ->
            spawnAbilityEntry(base, right, index, abilityItem, totalItems = items.size)
        }

        return PlayerRevealEntities(mannequin, nameDisplay, roleDisplay, abilityDescriptionDisplay, abilityEntities)
    }

    private fun spawnAbilityEntry(
        base: Location,
        right: Vector,
        index: Int,
        item: AbilityItem,
        totalItems: Int
    ): AbilityEntryEntities {
        val listBottom = ABILITY_START_Y - (totalItems - 1) * ABILITY_ROW_SPACING
        val topY = if (listBottom < ABILITY_MIN_Y) {
            ABILITY_START_Y + (ABILITY_MIN_Y - listBottom)
        } else {
            ABILITY_START_Y
        }
        val yOffset = topY - index * ABILITY_ROW_SPACING

        val rightOffset = right.clone().multiply(ABILITY_RIGHT_OFFSET)

        val labelLoc = base.clone().apply {
            add(rightOffset)
            add(right.clone().multiply(ABILITY_LABEL_EXTRA_OFFSET))
            y += yOffset
        }
        val itemLoc = base.clone().apply {
            add(rightOffset)
            y += yOffset
        }

        val itemDisplay = game.world.spawn(itemLoc, ItemDisplay::class.java) {
            it.isVisibleByDefault = false
            it.setItemStack(item.displayItem.createItemStack())
            it.billboard = Display.Billboard.CENTER
            it.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                AxisAngle4f(0f, 0f, 0f, 1f),
                Vector3f(SCALE_ABILITY_ITEM, SCALE_ABILITY_ITEM, SCALE_ABILITY_ITEM),
                AxisAngle4f(0f, 0f, 0f, 1f)
            )
            EntityManager.addEntityToRemoveOnEnd(game, it)
        }

        val labelDisplay = spawnTextDisplay(
            labelLoc,
            item.name,
            scale = SCALE_ABILITY_LABEL,
            alignment = TextDisplay.TextAlignment.LEFT
        )

        return AbilityEntryEntities(itemDisplay, labelDisplay, item.description)
    }

    fun onScroll(player: AmongUsPlayer, from: Int, to: Int) {
        val entities = playerEntities[player] ?: return
        val count = entities.abilityEntries.size
        if (count == 0) return

        val rawDelta = to - from
        val delta = when {
            rawDelta > 9 / 2 -> rawDelta - 9
            rawDelta < -9 / 2 -> rawDelta + 9
            else -> rawDelta
        }

        val current = playerAbilityIndex.getOrDefault(player, 0)
        val next = Math.floorMod(current + delta, count)

        if (next == current) return

        applyHighlight(entities.abilityEntries[current], highlighted = false)
        applyHighlight(entities.abilityEntries[next], highlighted = true)
        playerAbilityIndex[player] = next
        updateAbilityDescription(player)
    }

    private fun updateAbilityDescription(player: AmongUsPlayer) {
        val entities = playerEntities[player] ?: return
        val abilityDescription = entities.abilityDescriptionDisplay
        if (entities.abilityEntries.isEmpty()) return
        val abilityEntry = entities.abilityEntries[playerAbilityIndex[player] ?: 0]

        abilityDescription.text(abilityEntry.descriptionText)
    }

    private fun applyHighlight(entry: AbilityEntryEntities, highlighted: Boolean) {
        val interpolationDuration = if (highlighted) HIGHLIGHT_INTERPOLATION_TICKS else UNHIGHLIGHT_INTERPOLATION_TICKS

        entry.itemDisplay.isGlowing = highlighted
        val itemScale = if (highlighted) SCALE_ABILITY_ITEM_HOVER else SCALE_ABILITY_ITEM
        entry.itemDisplay.interpolationDuration = interpolationDuration
        entry.itemDisplay.interpolationDelay = 0
        entry.itemDisplay.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            AxisAngle4f(0f, 0f, 0f, 1f),
            Vector3f(itemScale, itemScale, itemScale),
            AxisAngle4f(0f, 0f, 0f, 1f)
        )

        val labelScale = if (highlighted) SCALE_ABILITY_LABEL_HOVER else SCALE_ABILITY_LABEL
        entry.labelDisplay.interpolationDuration = interpolationDuration
        entry.labelDisplay.interpolationDelay = 0
        entry.labelDisplay.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            AxisAngle4f(0f, 0f, 0f, 1f),
            Vector3f(labelScale, labelScale, labelScale),
            AxisAngle4f(0f, 0f, 0f, 1f)
        )

        entry.labelDisplay.backgroundColor = if (highlighted)
            Color.fromARGB(BG_ALPHA_HOVER, 240, 180, 30)
        else
            Color.fromARGB(BG_ALPHA, 15, 15, 15)

        val rawText = entry.rawLabelText
        val styledText = if (highlighted)
            rawText
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        else
            rawText.color(NamedTextColor.WHITE)

        entry.labelDisplay.text(styledText)
        entry.labelDisplay.textOpacity = if (highlighted) (-1).toByte() else TEXT_OPACITY
    }

    private fun spawnTextDisplay(
        loc: Location,
        text: Component,
        scale: Float,
        alignment: TextDisplay.TextAlignment = TextDisplay.TextAlignment.CENTER
    ): TextDisplay = game.world.spawn(loc, TextDisplay::class.java) {
        it.isVisibleByDefault = false
        it.text(text)
        it.billboard = Display.Billboard.CENTER
        it.backgroundColor = Color.fromARGB(BG_ALPHA, 15, 15, 15)
        it.isShadowed = true
        it.textOpacity = TEXT_OPACITY
        it.alignment = alignment
        it.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            AxisAngle4f(0f, 0f, 0f, 1f),
            Vector3f(scale, scale, scale),
            AxisAngle4f(0f, 0f, 0f, 1f)
        )
        EntityManager.addEntityToRemoveOnEnd(game, it)
    }

    private fun buildNameText(player: AmongUsPlayer): Component = textComponent {
        translatable("game.reveal_roles.name") {
            args {
                component("symbol") {
                    translatable("game.reveal_roles.name.symbol")
                    color(player.color.textColor)
                }
                string("name", player.name)
            }
        }
    }

    private fun buildRoleText(player: HumanAmongUsPlayer): Component {
        val role = player.assignedRole
            ?: return Component.translatable("game.reveal_roles.role.none")

        val teamColor = role.definition.team.textColor

        return textComponent {
            translatable("game.reveal_roles.role") {
                args {
                    component("role", role.definition.name.color(teamColor).decorate(TextDecoration.BOLD))
                    component("team") {
                        content(role.definition.team.name)
                        color(teamColor)
                    }
                }
            }
        }
    }

    private fun finish() {
        for ((_, entities) in playerEntities) entities.remove()
        playerEntities.clear()
        playerAbilityIndex.clear()
        viewEntity?.remove()
        viewEntity = null

        for (player in game.players) {
            player.mannequinController.showToSeeingPlayers()
            val p = player.humanOrNull?.player ?: continue
            p.isInvisible = false
            ignorePlayerStopSpectating = true
            p.setCamera(null)
            ignorePlayerStopSpectating = false
        }

        game.startGame()
    }

    @NMS
    private fun Player.setCamera(target: Entity?) {
        val handle = (this as CraftPlayer).handle
        val nmsEntity = (target as? CraftEntity)?.handle
        handle.setCamera(nmsEntity)
    }

    private data class AbilityEntryEntities(
        val itemDisplay: ItemDisplay,
        val labelDisplay: TextDisplay,
        val rawLabelText: Component,
        val descriptionText: Component = Component.empty(),
    ) {
        constructor(itemDisplay: ItemDisplay, labelDisplay: TextDisplay, descriptionText: Component = Component.empty()) :
                this(itemDisplay, labelDisplay, labelDisplay.text(), descriptionText)

        val all: List<Entity> get() = listOf(itemDisplay, labelDisplay)
    }

    private data class PlayerRevealEntities(
        val mannequin: Mannequin,
        val nameDisplay: TextDisplay,
        val roleDisplay: TextDisplay,
        val abilityDescriptionDisplay: TextDisplay,
        val abilityEntries: List<AbilityEntryEntities> = emptyList(),
    ) {
        val all: List<Entity>
            get() = buildList(4 + abilityEntries.sumOf { it.all.size }) {
                add(mannequin)
                add(nameDisplay)
                add(roleDisplay)
                add(abilityDescriptionDisplay)
                abilityEntries.forEach { addAll(it.all) }
            }

        fun showTo(player: Player) = all.forEach { player.showEntity(AmongUs, it) }
        fun remove() = all.forEach { it.remove() }
    }

    companion object {
        const val REVEAL_DURATION_TICKS = 10L * 20L // 10 seconds

        private const val CAMERA_DISTANCE = 3.75
        private const val CAMERA_EYE_HEIGHT = 1.62
        private const val CAMERA_PITCH = -5f

        private const val TEXT_LEFT_OFFSET = -1.8
        private const val NAME_HEIGHT = 2.3
        private const val ROLE_HEIGHT = 0.9
        private const val ABILITY_DESCRIPTION_HEIGHT = ROLE_HEIGHT + 2.0

        private const val SCALE_NAME = 1f
        private const val SCALE_BODY = 1f
        private const val BG_ALPHA = 160
        private const val TEXT_OPACITY = (-50).toByte()

        private const val ABILITY_RIGHT_OFFSET = 1.2
        private const val ABILITY_LABEL_EXTRA_OFFSET = 1.50

        private const val ABILITY_START_Y = 2.2
        private const val ABILITY_MIN_Y = 0.4
        private const val ABILITY_ROW_SPACING = 0.55

        private const val SCALE_ABILITY_ITEM = 0.50f
        private const val SCALE_ABILITY_ITEM_HOVER = 0.72f
        private const val SCALE_ABILITY_LABEL = 0.72f
        private const val SCALE_ABILITY_LABEL_HOVER = 0.90f

        private const val BG_ALPHA_HOVER = 220
        private const val HIGHLIGHT_INTERPOLATION_TICKS = 3
        private const val UNHIGHLIGHT_INTERPOLATION_TICKS = 5
    }
}