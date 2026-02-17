package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.util.CustomPersistentDataTypes
import com.fantamomo.mc.amongus.util.internal.MorphSkinManager
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
import org.slf4j.LoggerFactory
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class MorphManager(val game: Game) {
    private val morphs: MutableMap<AmongUsPlayer, MorphedPlayer> = mutableMapOf()

    private var ticks: Int = 0

    inner class MorphedPlayer(
        val player: AmongUsPlayer,
        val target: AmongUsPlayer,
        var frames: List<MorphSkinManager.Skin>? = null
    ) {
        val actionBar = player.game.actionBarManager.part(
            player,
            "morph",
            ActionBarManager.ActionBarPartType.RIGHT,
            100
        )

        var morphing: Boolean? = null
            private set
        private var animationFrames: List<MorphSkinManager.Skin>? = null
        private var animationIndex: Int = 0
        private var animationDelay: Int = 0
        private var animationOnFinish: (() -> Unit)? = null
        private val animationInterval = 5

        fun playForwardAnimation() {
            val frames = this.frames ?: return
            morphing = true
            playAnimation(frames) {
                actionBar.componentLike = com.fantamomo.mc.adventure.text.textComponent {
                    translatable("actionbar.morph.info") {
                        args {
                            string("player", target.name)
                        }
                    }
                }
            }
        }

        fun playBackwardAnimation(onFinish: () -> Unit) {
            val frames = this.frames ?: return
            morphing = false
            playAnimation(frames.reversed(), onFinish)
        }

        private fun playAnimation(
            frames: List<MorphSkinManager.Skin>,
            onFinish: (() -> Unit)? = null
        ) {
            animationFrames = frames
            animationIndex = 0
            animationDelay = 0
            animationOnFinish = onFinish
        }

        fun unmorph(animation: Boolean = true) {
            if (animation && frames != null) {
                playBackwardAnimation {
                    player.mannequinController.restoreAppearance()
                    actionBar.remove()
                    morphs.remove(player)
                }
            } else {
                player.mannequinController.restoreAppearance()
                actionBar.remove()
            }
        }

        fun tick() {
            val frames = animationFrames ?: return
            if (player.player == null) return

            if (++animationDelay < animationInterval) return
            animationDelay = 0

            val display = when (morphing) {
                true -> com.fantamomo.mc.adventure.text.textComponent {
                    translatable("actionbar.morph.info.morphing") {
                        args { string("player", target.name) }
                    }
                }
                false -> com.fantamomo.mc.adventure.text.textComponent {
                    translatable("actionbar.morph.info.unmorphing")
                }
                null -> null
            }

            if (display != null) {
                actionBar.componentLike = display
            }

            if (animationIndex >= frames.size) {
                animationFrames = null
                animationOnFinish?.invoke()
                animationOnFinish = null
                return
            }

            when (val frame = frames[animationIndex++]) {
                is MorphSkinManager.Skin.GeneratedSkin -> {
                    player.mannequinController.setSkinTexture(
                        frame.value,
                        frame.signature
                    )
                }

                is MorphSkinManager.Skin.PlayerProfileSkin -> {
                    val profile = frame.profile.id ?: return
                    val amongUsPlayer = PlayerManager.getPlayer(profile)
                    if (amongUsPlayer == null) {
                        logger.warn("Got $profile skin but there is no among us player with that profile")
                        return
                    }
                    player.mannequinController.copyAppearanceFrom(amongUsPlayer)
                }
            }
        }
    }

    fun isMorphed(player: AmongUsPlayer) = morphs.containsKey(player)

    fun getMorphedPlayer(player: AmongUsPlayer) = morphs[player]

    fun tick() {
        if (morphs.isEmpty()) return
        for (morphedPlayer in morphs.values) {
            morphedPlayer.tick()
        }
        ticks++
    }

    fun morph(player: AmongUsPlayer, target: AmongUsPlayer) {
        if (isMorphed(player)) return

        if (!AmongUsConfig.MorphBlender.enabled) {
            val morphed = MorphedPlayer(player, target)
            morphs[player] = morphed
            player.mannequinController.copyAppearanceFrom(target)
            return
        }

        val baseId = player.uuid.toString()
        val targetId = target.uuid.toString()

        val variants = 10

        val expectedHashes = (0..variants + 1).map {
            if (it == 0 || it == variants + 1) return@map null
            val t = it.toFloat() / (variants + 1)
            MorphSkinManager.buildHash(baseId, targetId, t)
        }

        val cached = expectedHashes.all { it == null || MorphSkinManager.getTexture(it) != null }

        val morphed = MorphedPlayer(player, target)
        morphs[player] = morphed

        if (cached) {
            val frames = expectedHashes.mapIndexed { index, hash ->
                if (hash == null) {
                    return@mapIndexed if (index == 0) MorphSkinManager.Skin.PlayerProfileSkin(player.profile, 0f)
                    else MorphSkinManager.Skin.PlayerProfileSkin(target.profile, 1f)
                }
                val data = MorphSkinManager.getTexture(hash)!!
                MorphSkinManager.Skin.GeneratedSkin(
                    hash,
                    index.toFloat() / (variants + 1),
                    MorphSkinManager.skinDir.resolve("$hash.png").toFile(),
                    data.value,
                    data.signature
                )
            }
            morphed.frames = frames
            morphed.playForwardAnimation()
        } else {
            player.mannequinController.copyAppearanceFrom(target)

            MorphSkinManager.pregenerateFromProfiles(
                player.profile,
                target.profile,
                variants
            ).thenAccept { frames ->
                morphed.frames = frames
                logger.debug("Morph cache generated for ${player.name}")
            }
        }
    }

    fun showMorphInventory(amongUsPlayer: AmongUsPlayer, callback: (Boolean) -> Unit) {
        val player = amongUsPlayer.player ?: return
        val morphInventory = MorphInventory(amongUsPlayer, callback)
        player.openInventory(morphInventory.inventory)
    }

    fun unmorph(player: AmongUsPlayer) {
        morphs[player]?.unmorph()
    }

    fun unmorphAll() {
        morphs.values.forEach { it.unmorph(animation = false) }
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
        private val logger = LoggerFactory.getLogger("AmongUs-MorphManager")
        private const val INVENTORY_SIZE = 54
        val SELECTED_PLAYER = NamespacedKey(AmongUs, "inv/morph/selected_player")
    }
}