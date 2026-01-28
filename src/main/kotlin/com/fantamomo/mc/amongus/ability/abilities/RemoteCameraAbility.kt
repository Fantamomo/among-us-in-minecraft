package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.manager.CameraManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import org.bukkit.Material

object RemoteCameraAbility :
    Ability<RemoteCameraAbility, RemoteCameraAbility.AssignedCameraAbility> {

    override val id: String = "remote_camera"

    override fun assignTo(player: AmongUsPlayer) = AssignedCameraAbility(player)

    class AssignedCameraAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<RemoteCameraAbility, AssignedCameraAbility> {
        var lastCamera: CameraManager.Camera? = null

        override val definition = RemoteCameraAbility

        override val items: List<AbilityItem> = listOf(
            abilityItem("camera") {

                material {
                    active = Material.ENDER_EYE
                    inactive = Material.BARRIER
                }

                name {
                    active("ability.remote_camera.camera.active")

                    inactive {
                        whenBlocked(
                            BlockReason.Sabotage,
                            "ability.general.disabled.sabotage"
                        )
                        whenBlocked(
                            BlockReason.InVent,
                            "ability.general.disabled.in_vent"
                        )
                        whenBlocked(
                            BlockReason.InMeeting,
                            "ability.general.disabled.in_meeting"
                        )
                        whenBlocked(
                            "inCams",
                            "ability.remote_camera.camera.already_in_cams"
                        )
                    }
                }

                blockWhen {
                    custom(BlockReason.Sabotage) {
                        game.sabotageManager.isSabotage(SabotageType.Communications)
                    }

                    inVent()
                    inMeeting()

                    custom("inCams") {
                        game.cameraManager.isInCams(player)
                    }
                }

                onRightClick {
                    val assigned = ability as AssignedCameraAbility
                    val last = assigned.lastCamera

                    if (last != null) {
                        game.cameraManager.joinCamera(player, last)
                    } else {
                        game.cameraManager.joinCamera(player)
                    }
                }
            }
        )
    }
}