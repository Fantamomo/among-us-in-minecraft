package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.abilities.RemoteCameraAbility
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
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
        var camera: Camera = camera
            set(value) {
                checkIsValid()
                if (field == value) return
                player.player?.showEntity(AmongUs, value.armorStand)
                setSpectatorTarget(value.armorStand)
                player.player?.sendBlockChange(value.location, Material.BARRIER.createBlockData())
                player.player?.hideEntity(AmongUs, field.armorStand)
                player.player?.sendBlockChange(field.location, Material.AIR.createBlockData())
                actionBar.componentLike = value.actionBarMessage
                field = value
                lastCameraChange = System.currentTimeMillis()
            }
        val actionBar = game.actionBarManager.part(
            player,
            "cams",
            ActionBarManager.ActionBarPartType.CENTER,
            200
        )

        init {
            val player = player.player
            if (player != null) {
                player.showEntity(AmongUs, camera.armorStand)
                player.sendBlockChange(camera.location, Material.BARRIER.createBlockData())
            }
            setSpectatorTarget(camera.armorStand)
            actionBar.componentLike = camera.actionBarMessage
            this.player.mannequinController.apply {
                showToSelf()
                freeze()
            }
        }

        private fun setSpectatorTarget(target: ArmorStand?) {
            val player = player.player ?: return
            val craftPlayer = player as CraftPlayer
            val handle = craftPlayer.handle
            if (target == null) handle.setCamera(handle)
            else handle.setCamera((target as CraftEntity).handle)
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
                lastPosition?.let { player.teleport(it) }
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

    class Camera(val name: String, val location: Location) {
        val uuid: Uuid = Uuid.random()

        val armorStand = location.world.spawn(location, ArmorStand::class.java) { armorStand ->
            armorStand.isVisible = false
            armorStand.setCanMove(false)
            armorStand.setGravity(false)
            armorStand.isMarker = true
            armorStand.isVisibleByDefault = false
        }.apply(EntityManager::addEntityToRemoveOnStop)

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
                cameraPlayer.player.player?.sendBlockChange(cameraPlayer.camera.location, Material.BARRIER.createBlockData())
            }
        }
    }
}