package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.AbilityManager
import com.fantamomo.mc.amongus.ability.abilities.RemoteCameraAbility
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.internal.NMS
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.ArmorStand
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class CameraManager(val game: Game) {
    private val cameras: List<Camera> = game.area.cams.map { Camera(it.key, it.value) }

    private val playersInCamera: MutableList<CameraPlayer> = mutableListOf()
    val cameraJoinPointMin = game.area.cameraJoinPointMin ?: throw IllegalStateException("CameraJoinPointMin not set")
    val cameraJoinPointMax = game.area.cameraJoinPointMax ?: throw IllegalStateException("CameraJoinPointMax not set")

    inner class CameraPlayer(val player: AmongUsPlayer, camera: Camera) {
        private var invalid: Boolean = false

        var lastCameraChange: Long = System.currentTimeMillis()
        var lastPosition: Location? = player.player?.location
        var ignorePlayerStopSpectatingEntityEvent: Boolean = false
            private set
        var camera: Camera = camera
            set(value) {
                checkIsValid()
                if (field == value) return
                switchCamera(field, value)
                field = value
            }
        val actionBar = game.actionBarManager.part(
            player,
            "cams",
            ActionBarManager.ActionBarPartType.CENTER,
            200
        )

        init {
            this.player.mannequinController.apply {
                showToSelf()
                freezeWithPhysics()
            }
            switchCamera(null, camera)
            AbilityManager.invalidatePlayer(this.player)
        }

        private fun switchCamera(old: Camera?, value: Camera) {
            val p = player.player
            p?.teleport(value.location)
            p?.showEntity(AmongUs, value.armorStand)
            setSpectatorTarget(value.armorStand)
            p?.sendBlockChange(value.location, Material.BARRIER.createBlockData())

            if (old != null) {
                p?.hideEntity(AmongUs, old.armorStand)
                p?.sendBlockChange(old.location, old.location.block.blockData)
            }

            actionBar.componentLike = value.actionBarMessage
            lastCameraChange = System.currentTimeMillis()

            // Schedule a task to run after 2 ticks.
            // If the new camera ArmorStand is out of range,
            // the player may not properly spectate it.
            // Since spectating is a client-side effect and cannot be verified server-side,
            // we delay the task by 2 ticks to ensure the player spectates the ArmorStand.
            AmongUs.server.scheduler.runTaskLater(AmongUs, { ->
                ignorePlayerStopSpectatingEntityEvent = true
                // We need to set it to null first because, in the ServerPlayer class,
                // an update packet is only sent when the new spectate target
                // differs from the previous one.
                setSpectatorTarget(null)
                ignorePlayerStopSpectatingEntityEvent = false
                setSpectatorTarget(value.armorStand)
            }, 2L)
        }

        /**
         * Uses `net.minecraft.server.level.ServerPlayer#setCamera` instead of
         * `org.bukkit.entity.Player#setSpectatorTarget`.
         *
         * We intentionally do NOT switch the player to `org.bukkit.GameMode.SPECTATOR`.
         * Spectator mode would grant additional spectator-specific abilities that we
         * explicitly want to avoid, such as:
         *
         * - Highlighting players (Spectator hotkey), which renders them with a glowing
         *   outline depending on their team.
         * - Seeing other spectators and invisible mobs.
         * - Teleporting to players via number key shortcuts (e.g., 1 for player list,
         *   2 for team members, including click-to-teleport behavior).
         *
         * Since we only want to change the camera perspective without enabling the
         * full spectator mechanics, we directly set the camera using NMS.
         */
        @NMS
        private fun setSpectatorTarget(target: ArmorStand?) {
            val player = player.player ?: return
            val craftPlayer = player as CraftPlayer
            val handle = craftPlayer.handle
            handle.setCamera((target as? CraftEntity)?.handle)
        }

        private fun checkIsValid() {
            if (invalid) throw IllegalStateException("CameraPlayer is invalid")
        }

        fun dispose() {
            checkIsValid()
            invalid = true

            actionBar.remove()

            val player = player.player
            if (player != null) {
                val loc = this.player.mannequinController.getEntity()?.location ?: lastPosition
                if (loc != null) player.teleport(loc)
                setSpectatorTarget(null)
                player.hideEntity(AmongUs, camera.armorStand)
                player.sendBlockChange(camera.location, Material.AIR.createBlockData())
            }
            this.player.mannequinController.apply {
                hideFromSelf()
                unfreeze()
                hideSneakingFor(500.milliseconds)
            }
            val ability = this.player.getAssignedAbility(RemoteCameraAbility)
            if (ability != null) {
                ability.lastCamera = camera
            }
        }
    }

    inner class Camera(val name: String, val location: Location) {
        val uuid: Uuid = Uuid.random()

        val armorStand = location.world.spawn(location, ArmorStand::class.java) { armorStand ->
            armorStand.isVisible = false
            armorStand.setCanMove(false)
            armorStand.setGravity(false)
            armorStand.isMarker = true
            armorStand.isVisibleByDefault = false
            armorStand.trackedBy
        }.also { EntityManager.addEntityToRemoveOnEnd(game, it) }

        val actionBarMessage = textComponent {
            translatable("camera.action_bar") {
                args {
                    component("camera") {
                        translatable("camera.name.$name")
                    }
                }
            }
        }
    }

    fun getCamera(player: AmongUsPlayer) = playersInCamera.find { it.player == player }

    fun joinCamera(amongUsPlayer: AmongUsPlayer) {
        joinCamera(amongUsPlayer, cameras.first())
    }

    fun joinCamera(player: AmongUsPlayer, camera: Camera) {
        var cameraPlayer = getCamera(player)
        if (cameraPlayer != null) return
        cameraPlayer = CameraPlayer(player, camera)
        playersInCamera.add(cameraPlayer)
    }

    fun isInCams(amongUsPlayer: AmongUsPlayer) = getCamera(amongUsPlayer) != null

    fun leaveCams(amongUsPlayer: AmongUsPlayer) {
        val cameraPlayer = getCamera(amongUsPlayer) ?: return
        cameraPlayer.dispose()
        playersInCamera.remove(cameraPlayer)
        AbilityManager.invalidatePlayer(amongUsPlayer)
    }

    private fun getNextCamera(camera: Camera): Camera {
        val index = cameras.indexOf(camera)
        return cameras[(index + 1) % cameras.size]
    }

    fun nextCame(amongUsPlayer: AmongUsPlayer, ignoreCooldown: Boolean = false) {
        val cameraPlayer = getCamera(amongUsPlayer) ?: return
        if (!ignoreCooldown && System.currentTimeMillis() - cameraPlayer.lastCameraChange < game.settings[SettingsKey.CAMERA_SWITCH_SAFE_COOLDOWN]) return
        cameraPlayer.camera = getNextCamera(cameraPlayer.camera)
    }

    fun tick() {
        val millis = System.currentTimeMillis()
        val cameraSwitchCooldown = game.settings[SettingsKey.CAMERA_SWITCH_SAFE_COOLDOWN]
        for (cameraPlayer in playersInCamera) {
            if (millis % 200 == 0L && millis - cameraPlayer.lastCameraChange > cameraSwitchCooldown) {
                cameraPlayer.player.player?.sendBlockChange(
                    cameraPlayer.camera.location,
                    Material.BARRIER.createBlockData()
                )
            }
        }
    }
}