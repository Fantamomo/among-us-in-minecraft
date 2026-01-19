package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.inventory.ItemStack
import kotlin.time.DurationUnit

@Suppress("UnstableApiUsage")
class DSLAbilityItem(
    private val builder: AbilityItemBuilder
) : AbilityItem(builder.ability, builder.id) {

    private var lastState: AbilityItemState? = null
    private var lastBlockReason: BlockReason? = null

    fun invalidate() {
        notifyItemChange()
    }

    private fun computeState(): Triple<AbilityItemState, BlockReason?, AbilityContext> {
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
            textComponent(ctx.player.locale) {
                if (state == AbilityItemState.ACTIVE) translatable(key)
                else translatable(key) {
                    args {
                        string("ability", id)
                        this@DSLAbilityItem.builder.cooldown?.remaining()?.toString(DurationUnit.SECONDS, 0)
                            ?.let { string("cooldown", it) }
                    }
                }
            }
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