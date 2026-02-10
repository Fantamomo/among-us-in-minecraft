package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.adventure.text.KTranslatableArgsBuilder
import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.UseCooldown
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.DurationUnit

class AbilityItemRender(
    val ctx: AbilityContext
) {
    lateinit var material: Material
    lateinit var translationKey: String

    var initialDefaultArgs: Boolean = true
    var cooldownName: String? = "cooldown"
    private var args: (KTranslatableArgsBuilder.() -> Unit)? = null

    fun nameArgs(block: KTranslatableArgsBuilder.() -> Unit) {
        args = block
    }

    @Suppress("UnstableApiUsage")
    fun toItemStack(): ItemStack {
        val item = ItemStack(material)

        val timer = cooldownName?.let { ctx.getTimer(it) }
        val remaining = timer?.remaining()
        val remainingSeconds = remaining?.toInt(DurationUnit.SECONDS)

        item.setData(
            DataComponentTypes.ITEM_NAME,
            textComponent(ctx.player.locale) {
                translatable(translationKey) {
                    args {
                        args?.invoke(this)
                        if (initialDefaultArgs) {
                            string("ability", ctx.ability.definition.id)
                            remainingSeconds?.let { string("cooldown", "${it}s")}
                        }
                    }
                }
            }
        )

        if (remainingSeconds != null && remainingSeconds > 0) {
            item.amount = remainingSeconds.coerceAtLeast(1)
            item.setData(
                DataComponentTypes.USE_COOLDOWN,
                UseCooldown.useCooldown(Float.MAX_VALUE / 2).cooldownGroup(ctx.cooldownKey)
            )
            ctx.player.player?.setCooldown(ctx.cooldownKey, Int.MAX_VALUE / 2)
        } else {
            ctx.player.player?.setCooldown(ctx.cooldownKey, 0)
        }

        return item
    }
}

@OptIn(ExperimentalContracts::class)
internal fun (AbilityItemRender.() -> Unit).toItemStack(ctx: AbilityContext): ItemStack {
    contract { callsInPlace(this@toItemStack, InvocationKind.EXACTLY_ONCE) }
    val render = AbilityItemRender(ctx)
    this(render)
    return render.toItemStack()
}