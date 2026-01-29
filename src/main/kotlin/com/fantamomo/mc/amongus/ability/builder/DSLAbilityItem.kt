package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.util.translateTo
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.minimessage.translation.Argument
import org.bukkit.inventory.ItemStack
import kotlin.time.DurationUnit

@Suppress("UnstableApiUsage")
class DSLAbilityItem<A : Ability<A, S>, S : AssignedAbility<A, S>>(
    private val builder: AbilityItemBuilder<A, S>
) : AbilityItem(builder.ability, builder.id) {

    private var lastState: AbilityItemState? = null
    private var lastBlockReason: BlockReason? = null

    fun invalidate() {
        notifyItemChange()
    }

    private fun computeState(): Triple<AbilityItemState, BlockReason?, AbilityContext<A, S>> {
        val cooldown = builder.cooldown
        if (cooldown?.isRunning() == false && !cooldown.isFinished()) {
            cooldown.start()
        }

        val ctx = builder.ctx

        val blockReason = builder.blockers.firstNotNullOfOrNull { it(ctx) }

        if (blockReason != null)
            return Triple(AbilityItemState.BLOCKED, blockReason, ctx)

        if (cooldown?.isFinished() == false)
            return Triple(AbilityItemState.COOLDOWN, null, ctx)

        return Triple(AbilityItemState.ACTIVE, null, ctx)
    }

    override fun getItemStack(): ItemStack {
        val (state, blockReason, ctx) = computeState()

        lastState = state
        lastBlockReason = blockReason

        val material = when (state) {
            AbilityItemState.ACTIVE -> builder.activeMaterial
            else -> builder.inactiveMaterial
        }

        val item = ItemStack(material)

        val key = when (state) {
            AbilityItemState.ACTIVE -> builder.nameProvider.active(ctx)
            AbilityItemState.BLOCKED -> builder.nameProvider.inactive(ctx, blockReason)
            AbilityItemState.COOLDOWN -> builder.nameProvider.cooldown
        }

        item.setData(
            DataComponentTypes.ITEM_NAME,
            ((key as? TranslatableComponent)?.run {
                val args = listOfNotNull(
                    Argument.string("ability", id),
                    this@DSLAbilityItem.builder.cooldown?.remaining()?.toString(DurationUnit.SECONDS, 0)
                        ?.let { Argument.string("cooldown", it) }
                )
                key.arguments(args)
            } ?: key).translateTo(ability.player.locale)
        )

        return item
    }

    override fun onRightClick() {
        val (state, _, ctx) = computeState()
        if (state != AbilityItemState.ACTIVE) return

        builder.rightClick(ctx)
        builder.cooldown?.reset(start = true)
        invalidate()
    }

    override fun onLeftClick() {
        builder.leftClick(builder.ctx)
        invalidate()
    }
}