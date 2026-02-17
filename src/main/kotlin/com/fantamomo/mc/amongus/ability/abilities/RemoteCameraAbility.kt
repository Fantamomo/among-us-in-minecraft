package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.manager.CameraManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import org.bukkit.Material

object RemoteCameraAbility :
    Ability<RemoteCameraAbility, RemoteCameraAbility.AssignedCameraAbility> {

    override val id = "remote_camera"

    override fun assignTo(player: AmongUsPlayer) =
        AssignedCameraAbility(player)

    class AssignedCameraAbility(
        override val player: AmongUsPlayer
    ) : AssignedAbility<RemoteCameraAbility, AssignedCameraAbility> {

        var lastCamera: CameraManager.Camera? = null

        override val definition = RemoteCameraAbility

        override val items = listOf(
            abilityItem("camera") {

                // ---------- CONDITIONS ----------

                condition {
                    if (game.sabotageManager.isSabotage(SabotageType.Communications))
                        BlockReason.Sabotage
                    else null
                }

                condition {
                    if (player.isVented())
                        BlockReason.InVent
                    else null
                }

                condition {
                    if (game.meetingManager.isCurrentlyAMeeting())
                        BlockReason.InMeeting
                    else null
                }

                condition {
                    if (game.cameraManager.isInCams(player))
                        BlockReason.custom("inCams")
                    else null
                }

                condition {
                    if (player.player?.isSneaking == true)
                        BlockReason.Custom("sneaking")
                    else null
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        material = Material.ENDER_EYE
                        translationKey = "ability.remote_camera.camera.active"
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

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {
                        material = Material.BARRIER
                        translationKey = when (val reason = ctx.getBlockReason()) {

                            BlockReason.Sabotage ->
                                "ability.general.disabled.sabotage"

                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom if (reason.id == "inCams") ->
                                "ability.remote_camera.camera.already_in_cams"

                            is BlockReason.Custom if (reason.id == "sneaking") ->
                                "ability.remote_camera.camera.sneaking"

                            else ->
                                "ability.remote_camera.camera.already_in_cams"
                        }
                    }
                }

                // ---------- COOLDOWN (unused but present) ----------

                state(AbilityItemState.COOLDOWN) {
                    render {
                        material = Material.BARRIER
                        translationKey = "ability.general.disabled.cooldown"
                    }
                }
            }
        )
    }
}