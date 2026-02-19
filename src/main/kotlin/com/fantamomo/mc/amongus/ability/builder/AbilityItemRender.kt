package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.adventure.text.KTranslatableArgsBuilder
import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.UseCooldown
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.inventory.meta.ItemMeta
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.DurationUnit

@Suppress("UnstableApiUsage")
class AbilityItemRender<M : ItemMeta>(
    val ctx: AbilityContext
) {
    lateinit var itemType: ItemType.Typed<M>
    lateinit var translationKey: String
    var amount: Int = 1
        set(value) {
            field = value.coerceIn(1, 99)
        }

    var initialDefaultArgs: Boolean = true
    var cooldownName: String? = "cooldown"
    private var args: (KTranslatableArgsBuilder.() -> Unit)? = null
    private var itemTypeConsumer: (M.() -> Unit)? = null

    fun itemMeta(block: M.() -> Unit) {
        itemTypeConsumer = block
    }

    fun nameArgs(block: KTranslatableArgsBuilder.() -> Unit) {
        args = block
    }

    @Suppress("UnstableApiUsage")
    fun toItemStack(): ItemStack {
        val item = itemType.createItemStack(amount, itemTypeConsumer)

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
                            remainingSeconds?.let { string("cooldown", "${it}s") }
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

@Suppress("UnstableApiUsage")
fun AbilityItemRender<ItemMeta>.itemType(itemType: ItemType) {
    this.itemType = itemType.typed()
}

@OptIn(ExperimentalContracts::class)
internal fun <M : ItemMeta> (AbilityItemRender<M>.() -> Unit).toItemStack(ctx: AbilityContext): ItemStack {
    contract { callsInPlace(this@toItemStack, InvocationKind.EXACTLY_ONCE) }
    val render = AbilityItemRender<M>(ctx)
    this(render)
    return render.toItemStack()
}