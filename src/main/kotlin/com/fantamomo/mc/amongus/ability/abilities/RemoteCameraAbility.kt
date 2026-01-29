package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.builder.AbilityItemState
import com.fantamomo.mc.amongus.ability.builder.BlockReason
import com.fantamomo.mc.amongus.ability.builder.abilityItem
import com.fantamomo.mc.amongus.manager.CameraManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

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

                // ---------- ACTIVE ----------

                state(AbilityItemState.ACTIVE) {

                    render {
                        ItemStack(Material.ENDER_EYE).apply {
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(
                                    "ability.remote_camera.camera.active"
                                )
                            )
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

                // ---------- BLOCKED ----------

                state(AbilityItemState.BLOCKED) {

                    render {

                        val reason = getBlockReason()

                        val key = when (reason) {

                            BlockReason.Sabotage ->
                                "ability.general.disabled.sabotage"

                            BlockReason.InVent ->
                                "ability.general.disabled.in_vent"

                            BlockReason.InMeeting ->
                                "ability.general.disabled.in_meeting"

                            is BlockReason.Custom ->
                                "ability.remote_camera.camera.already_in_cams"

                            else ->
                                "ability.remote_camera.camera.already_in_cams"
                        }

                        ItemStack(Material.BARRIER).apply {
                            setData(
                                DataComponentTypes.ITEM_NAME,
                                Component.translatable(key)
                            )
                        }
                    }
                }

                // ---------- COOLDOWN (unused but present) ----------

                state(AbilityItemState.COOLDOWN) {
                    render { ItemStack(Material.BARRIER) }
                }
            }
        )
    }
}