package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.WardrobeInventory
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.settings.SettingsInventory
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.log.elements.GameActionElements
import com.fantamomo.mc.amongus.util.translateTo
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.inventory.meta.ItemMeta

@Suppress("UnstableApiUsage")
class LobbyItemManger(val game: Game) {
    fun tick(tickContext: TickContext) {
        if (game.phase != GamePhase.LOBBY && game.phase != GamePhase.STARTING) return

        if (tickContext.isBy(20)) {
            updateAllPlayers()
        }
    }

    fun stop() {
        for (player in game.players) {
            if (player.isBot) continue
            val bukkitPlayer = player.player ?: continue
            bukkitPlayer.inventory.clear()
        }
    }

    fun addPlayer(player: HumanAmongUsPlayer) {
        if (game.phase != GamePhase.LOBBY) return

        updateAllPlayers()
    }

    fun onItemUse(player: HumanAmongUsPlayer, item: ItemStack) {
        val phase = game.phase
        if (phase != GamePhase.LOBBY && phase != GamePhase.STARTING) return
        val bukkitPlayer = player.player ?: return

        val itemType = item.itemMeta?.persistentDataContainer?.get(LOBBY_ITEM_TYPE_KEY, persistentDataType) ?: return

        if (!itemType.isAccessible(player)) return

        when (itemType) {
            LobbyItemType.START -> {
                if (phase == GamePhase.LOBBY) {
                    game.startStartCooldown()
                } else {
                    game.abortStartCooldown(GameActionElements.StartCountdownAborted.Reason.HOST)
                }
                updateAllPlayers()
            }

            LobbyItemType.LEAVE -> {
                PlayerManager.leaveGame(player)
                updateAllPlayers()
            }

            LobbyItemType.SETTINGS -> {
                val settingsInventory = SettingsInventory(player)
                bukkitPlayer.openInventory(settingsInventory.inventory)
            }

            LobbyItemType.COLOR -> {
                val wardrobeInventory = WardrobeInventory(player, WardrobeInventory.Type.COLOR)
                bukkitPlayer.openInventory(wardrobeInventory.inventory)
                game.updateAllWardrobeInventories()
            }

            LobbyItemType.ARMOR_TRIM_PATTERN -> {
                val wardrobeInventory = WardrobeInventory(player, WardrobeInventory.Type.PATTER)
                bukkitPlayer.openInventory(wardrobeInventory.inventory)
                game.updateAllWardrobeInventories()
            }

            LobbyItemType.ARMOR_TRIM_MATERIAL -> {
                val wardrobeInventory = WardrobeInventory(player, WardrobeInventory.Type.MATERIAL)
                bukkitPlayer.openInventory(wardrobeInventory.inventory)
                game.updateAllWardrobeInventories()
            }
        }
    }

    fun updateAllPlayers() {
        for (player in game.players) {
            if (player.isBot) continue
            updateItems(player)
        }
    }

    fun updateItems(player: HumanAmongUsPlayer) {
        val bukkitPlayer = player.player ?: return
        val inventory = bukkitPlayer.inventory
        val indexes = inventory.mapIndexedNotNull { index, stack ->
            val itemType = stack
                ?.itemMeta
                ?.persistentDataContainer
                ?.get(LOBBY_ITEM_TYPE_KEY, persistentDataType)
            if (itemType != null) Triple(index, stack, itemType) else null
        }

        for ((index, stack, itemType) in indexes) {
            if (!itemType.isAccessible(player)) continue
            val item = itemType.getItemType(player).createItemStack { config ->
                val displayName = itemType.getName(player)
                    .translateTo(bukkitPlayer.locale())
                    .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                config.displayName(displayName)
                config.persistentDataContainer.set(LOBBY_ITEM_TYPE_KEY, persistentDataType, itemType)
            }
            if (itemType.slot != index) inventory.setItem(index, null)
            inventory.setItem(itemType.slot, item)
        }
        for (itemType in LobbyItemType.entries) {
            if (!itemType.isAccessible(player)) continue
            if (indexes.none { it.third == itemType }) {
                val item = itemType.getItemType(player).createItemStack { config ->
                    val displayName = itemType.getName(player)
                        .translateTo(bukkitPlayer.locale())
                        .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                    config.displayName(displayName)
                    config.persistentDataContainer.set(LOBBY_ITEM_TYPE_KEY, persistentDataType, itemType)
                }
                inventory.setItem(itemType.slot, item)
            }
        }
    }

    companion object {
        private val persistentDataType = CustomPersistentDataTypes.enum<LobbyItemType>()
        val LOBBY_ITEM_TYPE_KEY = NamespacedKey(AmongUs, "lobby/item/type")
    }

    enum class LobbyItemType(val slot: Int) {
        START(4) {
            override fun isAccessible(player: HumanAmongUsPlayer) = player.isHost()
            override fun getItemType(player: HumanAmongUsPlayer) =
                if (player.game.phase == GamePhase.LOBBY) ItemType.GREEN_DYE else ItemType.RED_DYE

            override fun getName(player: HumanAmongUsPlayer) =
                Component.translatable(if (player.game.phase == GamePhase.LOBBY) "game.lobby.item.name.start" else "game.lobby.item.name.abort_start")
        },
        LEAVE(8) {
            override fun getItemType(player: HumanAmongUsPlayer) = ItemType.FIREWORK_STAR
            override fun getName(player: HumanAmongUsPlayer) = Component.translatable("game.lobby.item.name.leave")
        },
        SETTINGS(5) {
            override fun isAccessible(player: HumanAmongUsPlayer) = player.isHost()
            override fun getItemType(player: HumanAmongUsPlayer) = ItemType.REDSTONE
            override fun getName(player: HumanAmongUsPlayer) = Component.translatable("game.lobby.item.name.settings")
        },
        COLOR(0) {
            override fun getItemType(player: HumanAmongUsPlayer) = player.color.helmet
            override fun getName(player: HumanAmongUsPlayer) = textComponent {
                translatable("game.lobby.item.name.color") {
                    args {
                        string("color", player.color.capitalizeColoredNameWithoutColor)
                    }
                }
            }
        },
        ARMOR_TRIM_PATTERN(1) {
            override fun getItemType(player: HumanAmongUsPlayer) =
                player.armorTrim?.pattern?.let { WardrobeInventory.patternToItemType(it) }
                    ?: ItemType.SKULL_BANNER_PATTERN

            override fun getName(player: HumanAmongUsPlayer): Component {
                val pattern =
                    player.armorTrim?.pattern ?: return Component.translatable("game.lobby.item.name.pattern_none")
                return textComponent {
                    translatable("game.lobby.item.name.pattern") {
                        args {
                            component("pattern", pattern.description())
                        }
                    }
                }
            }
        },
        ARMOR_TRIM_MATERIAL(2) {
            override fun getItemType(player: HumanAmongUsPlayer) =
                player.armorTrim?.material?.let { WardrobeInventory.materialToItemType(it) } ?: ItemType.GOLD_INGOT

            override fun getName(player: HumanAmongUsPlayer): Component {
                val material =
                    player.armorTrim?.material ?: return Component.translatable("game.lobby.item.name.material_none")
                return textComponent {
                    translatable("game.lobby.item.name.material") {
                        args {
                            component("material", material.description())
                        }
                    }
                }
            }
        };

        open fun isAccessible(player: HumanAmongUsPlayer) = true

        abstract fun getItemType(player: HumanAmongUsPlayer): ItemType.Typed<out ItemMeta>

        abstract fun getName(player: HumanAmongUsPlayer): Component
    }
}