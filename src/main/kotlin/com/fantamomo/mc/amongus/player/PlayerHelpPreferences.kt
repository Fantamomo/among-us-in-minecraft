package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.util.sendComponent
import net.kyori.adventure.text.Component
import java.time.Duration
import java.util.*

class PlayerHelpPreferences(val owner: AmongUsPlayer) {
    private val hidden = EnumSet.noneOf(PlayerHelpMessage::class.java)
    private val cooldown: MutableMap<PlayerHelpMessage, Long> = mutableMapOf()

    enum class PlayerHelpMessage {
        LIGHTS,
        COMMUNICATIONS;

        val key = PREFIX + name.lowercase()
        val text = Component.translatable(key)
    }

    fun allow(help: PlayerHelpMessage): Boolean {
        return !hidden.contains(help) && cooldown[help]?.let { System.currentTimeMillis() - it > COOLDOWN_TIME }?.also { if (it) cooldown.remove(help) } ?: true
    }

    fun showHelp(help: PlayerHelpMessage) {
        if (allow(help)) {
            cooldown[help] = System.currentTimeMillis()
            showMessage(help)
        }
    }

    private fun showMessage(help: PlayerHelpMessage) {
        val player = owner.player ?: return
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
                    val p = owner.player ?: return@callback
                    if (p !== it) return@callback
                    hidden.add(help)
                    p.sendMessage(DEACTIVATED_TEXT)
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
    }
}