package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.*
import com.fantamomo.mc.amongus.manager.CameraManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemType

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

        @Suppress("UnstableApiUsage")
        override val items = listOf(
            abilityItem("camera") {
                clickDelay = true

                // ---------- CONDITIONS ----------

                condition(
                    BlockReason.Sabotage,
                    Component.translatable("ability.remote_camera.camera.tooltip.sabotaged")
                ) {
                    game.sabotageManager.isSabotage(SabotageType.Communications)
                }

                requiresNotInVent()

                requiresNotInMeeting()

                condition(
                    BlockReason.custom("inCams"),
                    Component.translatable("ability.remote_camera.camera.tooltip.in_cams")
                ) {
                    game.cameraManager.isInCams(player)
                }

                condition(
                    BlockReason.custom("sneaking"),
                    Component.translatable("ability.remote_camera.camera.tooltip.sneaking")
                ) {
                    player.player?.isSneaking == true
                }

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        itemType = ItemType.ENDER_EYE
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
                        itemType = ItemType.BARRIER
                        when (val reason = ctx.getBlockReason()) {

                            is BlockReason.Custom if (reason.id == "inCams") ->
                                translationKey = "ability.remote_camera.camera.already_in_cams"

                            is BlockReason.Custom if (reason.id == "sneaking") ->
                                translationKey = "ability.remote_camera.camera.sneaking"

                            else -> {}
                        }
                    }
                }
            }
        )
    }
}