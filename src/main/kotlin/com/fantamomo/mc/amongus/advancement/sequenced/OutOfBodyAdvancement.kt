package com.fantamomo.mc.amongus.advancement.sequenced

import com.fantamomo.mc.amongus.advancement.*
import com.fantamomo.mc.amongus.advancement.utils.step.step
import com.fantamomo.mc.amongus.events.roles.PhantomEnterGhostStateEvent
import com.fantamomo.mc.amongus.events.roles.PhantomExitGhostStateEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.role.imposters.PhantomRole
import org.bukkit.inventory.ItemType

@OptIn(ExperimentalAdvancementApi::class)
object OutOfBodyAdvancement : SequencedAdvancement("out_of_body") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.PHANTOM_MEMBRANE

    override fun applicableTo(player: HumanAmongUsPlayer) = player.role.definition == PhantomRole

    override val runner = createRunner {
        step("enter_ghost_state") { awaitOwn<PhantomEnterGhostStateEvent>() }
        step("kill") {
            forbidOwn(PhantomExitGhostStateEvent::class) {
                awaitOwnKill()
            }
        }
    }
}