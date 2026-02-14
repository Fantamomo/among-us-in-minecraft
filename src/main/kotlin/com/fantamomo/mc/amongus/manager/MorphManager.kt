package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class MorphManager(val game: Game) {
    private val morphs: MutableMap<AmongUsPlayer, MorphedPlayer> = mutableMapOf()

    class MorphedPlayer(
        val player: AmongUsPlayer,
        val target: AmongUsPlayer
    ) {
        init {
            player.mannequinController.copyAppearanceFrom(target)
        }

        fun unmorph() {
            player.mannequinController.restoreAppearance()
        }
    }

    fun isMorphed(player: AmongUsPlayer) = morphs.containsKey(player)

    fun getMorphedPlayer(player: AmongUsPlayer) = morphs[player]

    fun morph(player: AmongUsPlayer, target: AmongUsPlayer) {
        if (isMorphed(player)) return
        morphs[player] = MorphedPlayer(player, target)
    }

    fun showMorphInventory(amongUsPlayer: AmongUsPlayer, callback: (Boolean) -> Unit) {
        val player = amongUsPlayer.player ?: return
        val morphInventory = MorphInventory(amongUsPlayer, callback)
        player.openInventory(morphInventory.inventory)
    }

    fun unmorph(player: AmongUsPlayer) {
        morphs.remove(player)?.unmorph()
    }

    fun unmorphAll() {
        morphs.values.forEach { it.unmorph() }
        morphs.clear()
    }

    class MorphInventory(val player: AmongUsPlayer, private val callback: (Boolean) -> Unit) : InventoryHolder {
        private val inv =
            Bukkit.createInventory(this, INVENTORY_SIZE, Component.translatable("role.morphling.inventory.title"))

        override fun getInventory(): Inventory {
            setupInventory()
            return inv
        }

        @Suppress("UnstableApiUsage")
        private fun setupInventory() {
            val borderSlots = GuiAssignedTask.getBorderItemSlots(INVENTORY_SIZE)
            val border = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            borderSlots.forEach { inv.setItem(it, border) }

            val middleSlots = GuiAssignedTask.getMiddleItemSlots(INVENTORY_SIZE)
            var index = 0
            for (player in player.game.players) {
                if (player === this.player) continue
                val playerHead = ItemType.PLAYER_HEAD.createItemStack { skullMeta ->
                    skullMeta.playerProfile = player.profile
                }
                playerHead.setData(
                    DataComponentTypes.CUSTOM_NAME, // CUSTOM_NAME is used instead of ITEM_NAME since ITEM_NAME is ineffective on a PLAYER_HEAD that has a texture.
                    textComponent(this.player.locale) {
                        translatable("role.morphling.inventory.player_head") {
                            args {
                                string("player", player.name)
                            }
                        }
                        builder.decoration(TextDecoration.ITALIC, false)
                    }
                )
                playerHead.editPersistentDataContainer {
                    it.set(SELECTED_PLAYER, CustomPersistentDataTypes.UUID, player.uuid.toKotlinUuid())
                }
                inv.setItem(middleSlots[index++], playerHead)
            }
        }

        fun onClick(event: InventoryClickEvent) {
            if (player.game.morphManager.onClick(this, event)) {
                callback(true)
            }
        }

        fun onClose(event: InventoryCloseEvent) {
            callback(false)
        }
    }

    private fun onClick(inventory: MorphInventory, event: InventoryClickEvent): Boolean {
        val player = inventory.player
        val item = event.currentItem ?: return false
        val selectedPlayer =
            item.persistentDataContainer.get(SELECTED_PLAYER, CustomPersistentDataTypes.UUID) ?: return false
        val target = PlayerManager.getPlayer(selectedPlayer.toJavaUuid()) ?: return false
        player.player?.closeInventory()
        morph(player, target)
        return true
    }

    fun removePlayer(player: AmongUsPlayer) {
        unmorph(player)
    }

    companion object {
        private const val INVENTORY_SIZE = 54
        val SELECTED_PLAYER = NamespacedKey(AmongUs, "inv/morph/selected_player")
    }
}