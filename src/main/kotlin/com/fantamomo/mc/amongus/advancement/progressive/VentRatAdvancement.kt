package com.fantamomo.mc.amongus.advancement.progressive

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.space
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.abilities.VentAbility
import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.events.vents.VentEnterEvent
import com.fantamomo.mc.amongus.events.vents.VentExitEvent
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.util.sendComponent
import kotlinx.serialization.json.*
import net.kyori.adventure.title.TitlePart
import org.bukkit.inventory.ItemType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.days

/**
 * Enter a vent 15 times in a single game.
 *
 * Detects players who repeatedly enter and exit the same vent
 * to farm this advancement.
 */
object VentRatAdvancement : CounterAdvancement("vent_rat", 15) {
    private val logger: Logger = LoggerFactory.getLogger(VentRatAdvancement::class.java)

    @Suppress("UnstableApiUsage")
    override val icon: ItemType get() = ItemType.HOPPER

    /**
     * If this ever change, [CounterAdvancement.AssignedCounterAdvancement.toJson] must be updated
     */
    override val resetEachGame: Boolean = true

    val cheatingForget = 7.days.inWholeMilliseconds

    val cheaters: MutableMap<UUID, VentCheater> = mutableMapOf()

    data class VentCheater(val count: Int, val lastCheat: Long) {
        fun incrementCount() = VentCheater((count + 1).coerceAtMost(5), System.currentTimeMillis())
        fun decrementCount() = VentCheater((count - 1).coerceAtLeast(0), System.currentTimeMillis())
    }

    val THIRD_VENT_CHEATER = VentCheater(3, 0L)
    val EMPTY_VENT_CHEATER = VentCheater(0, 0L)

    override fun createOf(
        player: UUID,
        data: JsonElement
    ): AssignedCounterAdvancement {
        val cheater = cheaters[player]
        if (cheater == null) {
            val cheaterData = (data as? JsonObject)?.get("cheater") as? JsonObject
            if (cheaterData != null) {
                val count = (cheaterData["count"] as? JsonPrimitive)?.intOrNull
                val lastCheat = (cheaterData["last_cheat"] as? JsonPrimitive)?.longOrNull ?: 0L
                if (count != null) {
                    cheaters[player] = VentCheater(count, lastCheat)
                }
            }
        }
        return super.createOf(player, data)
    }

    override fun AdvancementListenerScope<AssignedCounterAdvancement>.listen() {
        val detector = VentBounceDetector()

        onOwn<VentEnterEvent> { event ->
            detector.onEnter(event.vent.uuid)

            // Check BEFORE incrementing, because if increment() returns 15 is would be too late.
            if (current >= 14) {
                if (detector.isCheating()) {

                    var cheaterValue = cheaters[player.uuid]

                    if (cheaterValue == null) {
                        cheaterValue = EMPTY_VENT_CHEATER
                    } else if (cheaterValue.count == 4 && cheaterValue.lastCheat < System.currentTimeMillis() - cheatingForget) {
                        cheaterValue = THIRD_VENT_CHEATER
                    }

                    cheaterValue = cheaterValue.incrementCount()
                    cheaters[player.uuid] = cheaterValue

                    try {
                        player.player?.sendComponent {
                            translatable("advancement.vent_rat.cheater")
                            space()
                            val key = when (cheaterValue.count) {
                                0 -> throw IllegalStateException("VentCheater count should never be 0")
                                1 -> "advancement.vent_rat.cheater.first"
                                2 -> "advancement.vent_rat.cheater.second"
                                3 -> "advancement.vent_rat.cheater.third"
                                4 if (player.role.definition.team == Team.IMPOSTERS) -> "advancement.vent_rat.cheater.fourth.imposter"
                                4 -> "advancement.vent_rat.cheater.fourth.other"
                                5 -> "advancement.vent_rat.cheater.fifth"
                                else -> throw IllegalStateException("VentCheater count should never be greater than 5")
                            }
                            translatable(key)
                        }
                    } catch (e: IllegalStateException) {
                        logger.error("Failed to send VentRat cheater advancement message", e)
                    }

                    when (cheaterValue.count) {
                        3 -> {
                            player.player?.sendTitlePart(TitlePart.TITLE, textComponent {
                                translatable("advancement.vent_rat.cheater.third.big")
                            })
                        }
                        5 -> {
                            if (player.role.definition.team == Team.IMPOSTERS) {
                                player.game.sendTitle(TitlePart.TITLE, textComponent {
                                    translatable("advancement.vent_rat.cheater.announce") {
                                        args {
                                            string("name", player.name)
                                        }
                                    }
                                })
                            } else {
                                val ventAbility = player.getAssignedAbility(VentAbility)
                                ventAbility?.markedAsVentCheater = true
                            }
                        }
                    }

                    reset()
                    detector.clear()
                    return@onOwn
                }
            }

            increment()
        }

        onOwn<VentExitEvent> { event ->
            detector.onExit(event.vent.uuid)
        }
    }
}
