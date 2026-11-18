@file:OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class, UnsafeDuringIrConstructionAPI::class)

package com.fantamomo.mc.amongus.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * Fills in the `partIds` argument of every `createRunner { ... }` call with the constant keys
 * of all `withPart("...")` calls inside the runner lambda (nested ones included), in source order.
 *
 * The lambda itself is left untouched and is never executed by this plugin.
 *
 * @author Fantamomo
 * @since 2.5-SNAPSHOT
 */
class AdvancementPartsRegistrar : CompilerPluginRegistrar() {
    override val pluginId = "com.fantamomo.mc.amongus.advancement-parts"
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(AdvancementPartsExtension(configuration.messageCollector))
    }
}

private const val CREATE_RUNNER = "com.fantamomo.mc.amongus.advancement.createRunner"
private const val WITH_PART = "com.fantamomo.mc.amongus.advancement.utils.step.step"
private const val PART_IDS_PARAMETER = "partIds"

private class AdvancementPartsExtension(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            file.transformChildren(CreateRunnerTransformer(file, pluginContext), null)
        }
    }

    private inner class CreateRunnerTransformer(
        private val file: IrFile,
        private val context: IrPluginContext,
    ) : IrTransformer<Nothing?>() {

        override fun visitCall(expression: IrCall, data: Nothing?): IrElement {
            expression.transformChildren(this, null)

            val function = expression.symbol.owner
            if (function.kotlinFqName.asString() != CREATE_RUNNER) return expression

            val partIdsParameter = function.parameters.firstOrNull { it.name.asString() == PART_IDS_PARAMETER }
                ?: return expression.also { report(it, "createRunner has no '$PART_IDS_PARAMETER' parameter") }
            // explicitly passed by the caller, leave it alone
            if (expression.arguments[partIdsParameter] != null) return expression

            val ids = LinkedHashSet<String>()
            for (argument in expression.arguments) argument?.let { collectPartIds(it, ids) }

            val builtIns = context.irBuiltIns
            val builder = DeclarationIrBuilder(context, expression.symbol, expression.startOffset, expression.endOffset)
            expression.arguments[partIdsParameter] = builder.irCall(
                builtIns.arrayOf,
                builtIns.arrayClass.typeWith(builtIns.stringType),
                listOf(builtIns.stringType),
            ).apply {
                arguments[0] = builder.irVararg(builtIns.stringType, ids.map { builder.irString(it) })
            }
            return expression
        }

        /** Collects the keys of all `withPart` calls in [root] in pre-order, i.e. outer parts before nested ones. */
        private fun collectPartIds(root: IrExpression, ids: MutableSet<String>) {
            root.acceptChildrenVoid(object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)

                override fun visitCall(expression: IrCall) {
                    val function = expression.symbol.owner
                    if (function.kotlinFqName.asString() == WITH_PART) {
                        val keyParameter = function.parameters.first { it.name.asString() == "key" }
                        val key = expression.arguments[keyParameter]
                        val value = (key as? IrConst)?.value as? String
                        if (value != null) ids += value
                        else report(key ?: expression, "withPart key must be a constant string")
                    }
                    expression.acceptChildrenVoid(this)
                }
            })
        }

        private fun report(element: IrElement, message: String) {
            val entry = file.fileEntry
            val location: CompilerMessageSourceLocation? = CompilerMessageLocation.create(
                entry.name,
                entry.getLineNumber(element.startOffset) + 1,
                entry.getColumnNumber(element.startOffset) + 1,
                null,
            )
            messages.report(CompilerMessageSeverity.ERROR, message, location)
        }
    }
}
