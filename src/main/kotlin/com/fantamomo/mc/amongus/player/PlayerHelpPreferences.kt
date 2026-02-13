package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.util.sendComponent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.time.Duration
import java.util.*

class PlayerHelpPreferences private constructor(
    private val hidden: EnumSet<PlayerHelpMessage>
) {
    private val cooldown: MutableMap<PlayerHelpMessage, Long> = mutableMapOf()

    constructor() : this(EnumSet.noneOf(PlayerHelpMessage::class.java))

    enum class PlayerHelpMessage {
        LIGHTS,
        COMMUNICATIONS;

        val key = PREFIX + name.lowercase()
        val text = Component.translatable(key)
    }

    fun allow(help: PlayerHelpMessage): Boolean {
        return !hidden.contains(help) && cooldown[help]?.let { System.currentTimeMillis() - it > COOLDOWN_TIME }
            ?.also { if (it) cooldown.remove(help) } ?: true
    }

    fun hide(help: PlayerHelpMessage) = hidden.add(help)

    fun showHelp(help: PlayerHelpMessage, player: Player) {
        if (allow(help)) {
            cooldown[help] = System.currentTimeMillis()
            showMessage(help, player)
        }
    }

    fun toJsonElement(): JsonElement {
        if (hidden.isEmpty()) return JsonPrimitive(false)
        if (hidden.containsAll(ALL)) return JsonPrimitive(true)
        return JsonArray(hidden.map { JsonPrimitive(it.name) })
    }

    private fun showMessage(help: PlayerHelpMessage, player: Player) {
        player.sendComponent {
            append(HEADER_TEXT)
            newLine()
            append(help.text)
            hoverEvent(KHoverEventType.ShowText) {
                translatable(DEACTIVATE_KEY)
            }
            clickEvent(KClickEventType.Callback) {
                uses(1)
                lifetime(Duration.ofMinutes(5))
                callback {
                    if (player !== it) return@callback
                    hidden.add(help)
                    player.sendMessage(DEACTIVATED_TEXT)
                }
            }
        }
    }

    companion object {
        private const val COOLDOWN_TIME = 10L * 1000L // 10 seconds

        private const val PREFIX = "player.help."

        private const val HEADER_KEY = "player.help.header"
        private const val DEACTIVATE_KEY = "player.help.deactivate"
        private const val DEACTIVATED_KEY = "player.help.deactivated"
        private val HEADER_TEXT = Component.translatable(HEADER_KEY)

        private val DEACTIVATED_TEXT = Component.translatable(DEACTIVATED_KEY)
        private val ALL = EnumSet.allOf(PlayerHelpMessage::class.java)

        fun fromJsonElement(json: JsonElement) = when (json) {
            is JsonPrimitive if (json.boolean) -> {
                PlayerHelpPreferences(ALL.clone())
            }

            is JsonPrimitive -> {
                PlayerHelpPreferences()
            }

            is JsonArray -> {
                val data = EnumSet.noneOf(PlayerHelpMessage::class.java)
                for (element in json) {
                    if (element !is JsonPrimitive) continue
                    try {
                        data.add(PlayerHelpMessage.valueOf(element.content.uppercase()))
                    } catch (e: Exception) {
                        continue
                    }
                }
                PlayerHelpPreferences(data)
            }

            else -> PlayerHelpPreferences()
        }
    }
}