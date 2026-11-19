package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.progressive.TaskMasterAdvancement
import com.fantamomo.mc.amongus.advancement.progressive.VentRatAdvancement
import com.fantamomo.mc.amongus.advancement.progressive.VeteranAdvancement
import com.fantamomo.mc.amongus.advancement.sequenced.*
import com.fantamomo.mc.amongus.advancement.single.FirstGameAdvancement
import com.fantamomo.mc.amongus.advancement.single.GhostVictoryAdvancement
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import kotlinx.serialization.json.JsonElement
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

interface Advancement<A : Advancement<A, AA>, AA : AssignedAdvancement<A, AA>> {
    val id: String

    /**
     * The name of the advancement.
     */
    val name: Component
        get() = Component.translatable("advancement.$id")

    /**
     * The description of the advancement shown to the player who can earn it.
     *
     * Describes the actions required to complete the advancement.
     * Should be phrased as an instruction or objective.
     *
     * Example: "Exit a vent, kill a player, and re-enter the same vent within 10 seconds."
     */
    val description: Component
        get() = Component.translatable("advancement.$id.description")

    /**
     * The description of the advancement shown to other players when it is earned.
     *
     * Describes the achievement as an accomplished action, rather than an objective.
     *
     * Example: "Exited a vent, killed a player, and re-entered the same vent within 10 seconds."
     */
    val descriptionOther: Component
        get() = Component.translatable("advancement.$id.description.other")

    /**
     * Should return true if displaying this advancement would give the player an advantage in the game.
     *
     * If true, we award the player with this advancement **after** the game finishes.
     * If false, we award the player with this advancement the moment they complete it.
     *
     * Other players still only see the advancement after the game finishes.
     */
    val hasGameAdvantage: Boolean
        get() = false

    /**
     * If true, the name and description are hidden in the inventory GUI until the advancement is completed.
     */
    val isSecret: Boolean
        get() = false

    /**
     * The icon representing this advancement in the inventory GUI.
     */
    @Suppress("UnstableApiUsage")
    val icon: ItemType
        get() = ItemType.BOOK

    fun new(player: UUID): AA

    fun createOf(player: UUID, data: JsonElement): AA

    /**
     * Returns the state of this advancement for [player] (loading or creating it if needed).
     *
     * Use this to update the advancement directly from code, e.g. `MyAdvancement.of(player).award()`.
     */
    fun of(player: UUID): AA {
        @Suppress("UNCHECKED_CAST")
        return AdvancementManager.getAssigned(player, this as A)
    }

    fun of(player: HumanAmongUsPlayer): AA = of(player.uuid)

    companion object {
        internal const val GOING_THROUGH_SEQUENCES_DISABLED = 10000
        internal const val GOING_THROUGH_SEQUENCES_LIMIT = 100
        internal const val GOING_THROUGH_SEQUENCES_WARNING = 40
        internal const val GOING_THROUGH_SEQUENCES_INCREMENT = 7
        internal const val GOING_THROUGH_SEQUENCES_DECREMENT = 1

        val advancements = listOf<Advancement<*, *>>(
            // progressiv
            TaskMasterAdvancement,
            VentRatAdvancement,
            VeteranAdvancement,

            // sequenced
            FastVentKillAdvancement,
            HiredGunFiredGunAdvancement,
            IdentityTheftAdvancement,
            SelfReportAdvancement,
            StandingOvationAdvancement,
            ThePerfectKillAdvancement,
            TwoForOneAdvancement,
            WhoTurnedOffTheLightsAdvancement,

            // single
            FirstGameAdvancement,
            GhostVictoryAdvancement,
        )

        val advancementMap = advancements.associateBy { it.id }

        /**
         * This is used to detect and disabled advancements where the runner does not actually wait for something.
         * If we wouldnt block the advancement, it would just be an infinity loop.
         */
        @OptIn(ExperimentalAdvancementApi::class)
        internal val goingThroughSequences = mutableMapOf<SequencedAdvancement, AtomicInteger>()
    }
}