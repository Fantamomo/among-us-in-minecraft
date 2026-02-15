package com.fantamomo.mc.amongus.task.tasks

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.task.Task
import com.fantamomo.mc.amongus.task.TaskType
import com.fantamomo.mc.amongus.task.areaLocation
import com.fantamomo.mc.amongus.util.hideTooltip
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

object StoreArtifactsTask : Task<StoreArtifactsTask, StoreArtifactsTask.AssignedStoreArtifacts> {
    override val id: String = "store_artifacts"
    override val type: TaskType = TaskType.SHORT

    override fun isAvailable(game: Game) = !game.area.tasks[id].isNullOrEmpty()

    override fun assignTo(player: AmongUsPlayer) = AssignedStoreArtifacts(player)

    class AssignedStoreArtifacts(override val player: AmongUsPlayer) :
        GuiAssignedTask<StoreArtifactsTask, AssignedStoreArtifacts>() {
        override val task = StoreArtifactsTask
        override val inv: Inventory =
            Bukkit.createInventory(this, SIZE, Component.translatable("tasks.store_artifacts.title"))
        override val location: Location = areaLocation ?: error("No location found for $id")
        private val selectedArtefacts = artefacts.shuffled().take(4)
        private val requiredArtefacts by lazy {
            selectedArtefacts.shuffled().takeIf { it != selectedArtefacts } ?: selectedArtefacts.reversed()
        }
        private var shouldCheck = 0

        override fun setupInventory() {
            val border = itemStack(Material.BLACK_STAINED_GLASS_PANE).hideTooltip()
            repeat(SIZE) { inv.setItem(it, border) }

            selectedArtefacts.forEachIndexed { index, material ->
                inv.setItem(
                    artefactSlots[index],
                    itemStack(material).hideTooltip().markAsMoveable().markWith("artefact")
                )
            }
            for (slot in artefactTargetSlot) inv.setItem(slot, null)
        }

        override fun onInventoryClick(event: InventoryClickEvent) {
            val item = event.currentItem?.takeIf { it.isMine() } ?: return
            if (!item.isMarkedWith("artefact")) return
            shouldCheck = 10
        }

        override fun tick() {
            if (shouldCheck <= 0) return
            shouldCheck--
            var correct = 0
            artefactTargetSlot.forEachIndexed { index, i ->
                val current = inv.getItem(i)
                val target = requiredArtefacts[index]
                if (current?.type == target) {
                    correct++
                    inv.setItem(i + 9, itemStack(Material.GREEN_STAINED_GLASS_PANE).hideTooltip())
                } else {
                    inv.setItem(i + 9, itemStack(Material.RED_STAINED_GLASS_PANE).hideTooltip())
                }
            }
            if (correct == 4) player.game.taskManager.completeTask(this)
        }

        companion object {
            const val SIZE = 36
            private val artefactSlots = listOf(2, 3, 5, 6)
            private val artefactTargetSlot = artefactSlots.map { it + 18 }

            private val artefacts = listOf(
                Material.SKELETON_SKULL,
                Material.BONE,
                Material.OAK_SAPLING,
                Material.TURTLE_SCUTE,
                Material.EGG
            )
        }
    }
}