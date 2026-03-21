package com.fantamomo.mc.amongus.player

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.task.TaskManager
import net.kyori.adventure.audience.Audience
import org.bukkit.Location
import org.bukkit.entity.Mannequin
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

sealed class AbstractAmongUsPlayer(
    override val uuid: UUID,
    override val game: Game
) : AmongUsPlayer {
    abstract override val name: String
    abstract override val locale: Locale
    abstract override val profile: PlayerProfile

    abstract override var color: PlayerColor
        internal set

    abstract override var armorTrim: ArmorTrim?
        internal set

    override val visibleColor: PlayerColor
        get() {
            val camouflageTarget = game.morphManager.camouflageTarget()
            if (camouflageTarget != null) return camouflageTarget.color
            val morphPlayer = game.morphManager.getMorphedPlayer(this)
            return morphPlayer?.target?.color ?: color
        }

    internal var assignedRole: AssignedRole<*, *>? = null
    override val role: AssignedRole<*, *>
        get() = assignedRole ?: throw IllegalStateException("Role not assigned")

    override var modification: AssignedModification<*, *>? = null
        internal set

    override val tasks: Set<TaskManager.RegisteredTask>
        get() = game.taskManager.get(this)

    internal val _abilities: MutableList<AssignedAbility<*, *>> = mutableListOf()
    override val abilities: List<AssignedAbility<*, *>>
        get() = _abilities

    override var deadReason: DeadReason? = null
        internal set

    override val location: Location
        get() = mannequinController.getEntity()?.location ?: throw IllegalStateException("No location available")
    override var meetingButtonsPressed: Int = 0
    override val mannequinController = MannequinController(this)

    abstract override val audience: Audience


    // Functions

    internal open fun updateHelmet() {
        val helmet = color.toItemStack(armorTrim)
        if (this is HumanAmongUsPlayer) {
            player?.inventory?.helmet = helmet
            if (_wardrobeMannequin is Mannequin) wardrobeMannequin?.equipment?.helmet = helmet
        }
        if (!game.morphManager.isMorphed(this)) {
            mannequinController.getEntity()?.equipment?.helmet = helmet
        }
        game.updateAllWardrobeInventories()
    }

    fun addNewAbility(ability: Ability<*, *>) {
        if (!game.phase.isPlaying && game.phase != GamePhase.REVEALING_ROLES) throw IllegalStateException("Cannot add ability in this phase")
        if (!ability.canAssignTo(this)) throw IllegalArgumentException("Ability cannot be assigned to this player")
        val assigned = ability.assignTo(this)
        AbilityManager.registerAbility(assigned)
        _abilities.add(assigned)
        if (game.phase == GamePhase.REVEALING_ROLES) return
        val player = (this as? HumanAmongUsPlayer)?.player
        for (item in assigned.items) {
            item.startCooldown()
            player?.inventory?.addItem(item.get())
        }
    }

    internal open fun notifyAbilityItemChange(item: AbilityItem) {}

    internal open fun preStart() {

    }

    internal open fun start() {

    }

    internal fun addGhostImprovements() {
        if (isAlive()) return
        internalEntity?.addPotionEffect(GHOST_SPEED)
    }

    override fun hasAbility(ability: Ability<*, *>) = abilities.any { it.definition === ability }

    companion object {
        private val GHOST_SPEED = PotionEffect(PotionEffectType.SPEED, -1, 1, false, false)
    }
}