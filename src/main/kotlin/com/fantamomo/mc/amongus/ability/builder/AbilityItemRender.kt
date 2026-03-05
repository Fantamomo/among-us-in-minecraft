package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.util.textComponent
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.UseCooldown
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.translation.Argument
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
    private var displayNameObject: Any? =
        (ctx.getBlockReason()?.defaultBlockMessage as? TranslatableComponent)?.arguments(Argument.string("ability", ctx.ability.definition.id))
    var displayName: Component
        get() = when (val obj = displayNameObject) {
            is Component -> obj
            is String -> Component.translatable(obj)
            null -> throw IllegalStateException("displayName not set")
            else -> throw IllegalArgumentException("Invalid displayName type: ${obj::class.simpleName}")
        }
        set(value) {
            displayNameObject = value
        }
    var translationKey: String
        get() = when (val obj = displayNameObject) {
            is String -> obj
            is TranslatableComponent -> obj.key()
            is Component -> throw IllegalArgumentException("translationKey cannot be set from a Component")
            null -> throw IllegalStateException("translationKey not set")
            else -> throw IllegalArgumentException("Invalid translationKey type: ${obj::class.simpleName}")
        }
        set(value) {
            displayNameObject = value
        }

    private var overrideAmount: Boolean = false

    var amount: Int = 1
        set(value) {
            field = value.coerceIn(1, 99)
            overrideAmount = true
        }

    var initialDefaultArgs: Boolean = true
    var cooldownName: String? = "cooldown"
    var useCustomName: Boolean = false
    private var itemTypeConsumer: (M.() -> Unit)? = null

    fun itemMeta(block: M.() -> Unit) {
        itemTypeConsumer = block
    }

    @Suppress("UnstableApiUsage")
    fun toItemStack(): ItemStack {
        val displayNameObject = displayNameObject ?: throw IllegalStateException("displayName not set")
        val item = itemType.createItemStack(amount, itemTypeConsumer)

        val timer = cooldownName?.let { ctx.getTimer(it) }
        val remaining = timer?.remaining()
        val remainingSeconds = remaining?.toInt(DurationUnit.SECONDS)

        item.setData(
            if (useCustomName) DataComponentTypes.CUSTOM_NAME else DataComponentTypes.ITEM_NAME,
            textComponent(ctx.player.locale) {
                when (displayNameObject) {
                    is String if (initialDefaultArgs) -> translatable(translationKey) {
                        args {
                            string("ability", ctx.ability.definition.id)
                            remainingSeconds?.let { string("cooldown", "${it}s") }
                        }
                    }

                    is String -> translatable(displayNameObject)
                    is Component -> append(displayNameObject)
                    else -> throw IllegalArgumentException("Invalid displayName type: ${displayNameObject::class.simpleName}")
                }
            }
        )

        val lore = ItemLore.lore()

        for (condition in ctx.builder.conditions) {
            val booleanCondition = condition as? BooleanAbilityCondition ?: continue
            val blocked = booleanCondition.blocked(ctx)
            val reason = booleanCondition.reason
            val tooltipMessage = (booleanCondition as? TooltipAbilityCondition)?.tooltip ?: reason.tooltipMessage ?: continue
            lore.addLine(textComponent(ctx.player.locale) {
                if (blocked) translatable("ability.general.util.no")
                else translatable("ability.general.util.yes")
                space()
                append(tooltipMessage)
                italic(TextDecoration.State.FALSE)
                color(if (blocked) NamedTextColor.RED else NamedTextColor.GREEN)
            })
        }

        val hasCooldown = timer != null
        var isCooldownActive = false

        if (remainingSeconds != null && remainingSeconds > 0) {
            if (!overrideAmount) item.amount = remainingSeconds.coerceAtLeast(1)
            item.setData(
                DataComponentTypes.USE_COOLDOWN,
                UseCooldown.useCooldown(Float.MAX_VALUE / 2).cooldownGroup(ctx.cooldownKey)
            )
            ctx.player.player?.setCooldown(ctx.cooldownKey, Int.MAX_VALUE / 2)
            isCooldownActive = true
        } else {
            ctx.player.player?.setCooldown(ctx.cooldownKey, 0)
        }

        if (hasCooldown) {
            lore.addLine(textComponent(ctx.player.locale) {
                if (isCooldownActive) translatable("ability.general.util.no")
                else translatable("ability.general.util.yes")
                space()
                italic(TextDecoration.State.FALSE)
                translatable("ability.general.tooltip.cooldown")
                color(if (isCooldownActive) NamedTextColor.RED else NamedTextColor.GREEN)
            })
        }

        item.setData(
            DataComponentTypes.LORE,
            lore
        )

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