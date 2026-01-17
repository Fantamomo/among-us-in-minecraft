package com.fantamomo.mc.amongus.ability.abilities

import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.ability.item.DeactivatableAbilityItem
import com.fantamomo.mc.amongus.ability.item.game
import com.fantamomo.mc.amongus.manager.CameraManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object RemoteCameraAbility : Ability<RemoteCameraAbility, RemoteCameraAbility.AssignedCameraAbility> {
    override val id: String = "remote_camera"

    override fun assignTo(player: AmongUsPlayer) = AssignedCameraAbility(player)

    class AssignedCameraAbility(override val player: AmongUsPlayer) :
        AssignedAbility<RemoteCameraAbility, AssignedCameraAbility> {

        var lastCamera: CameraManager.Camera? = null

        override val definition = RemoteCameraAbility
        override val items: List<AbilityItem> = listOf(CameraAbilityItem(this))
    }

    @Suppress("UnstableApiUsage")
    class CameraAbilityItem(ability: AssignedCameraAbility) : DeactivatableAbilityItem(ability, "camera") {
        override fun activatedItem() = ItemStack(Material.ENDER_EYE).apply {
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable("ability.remote_camera.camera.active")
                }
            )
        }

        override fun deactivatedItem() = ItemStack(Material.BARRIER).apply {
            val key = when {
                game.sabotageManager.isSabotage(SabotageType.Communications) -> "ability.remote_camera.camera.sabotage"
                ability.player.isVented() -> "ability.remote_camera.camera.vented"
                else -> "ability.remote_camera.camera.already_in_cams"
            }
            setData(
                DataComponentTypes.ITEM_NAME,
                textComponent(ability.player.locale) {
                    translatable(
                        key
                    )
                }
            )
        }

        override fun canUse() =
            !(game.sabotageManager.isSabotage(SabotageType.Communications) || game.cameraManager.isInCams(ability.player))

        override fun onRightClick() {
            if (!canUse()) return
            val player = ability.player
            val ability = ability as AssignedCameraAbility

            val lastCamera = ability.lastCamera
            if (lastCamera != null) {
                game.cameraManager.joinCamera(player, lastCamera)
            } else {
                game.cameraManager.joinCamera(player)
            }
        }
    }
}