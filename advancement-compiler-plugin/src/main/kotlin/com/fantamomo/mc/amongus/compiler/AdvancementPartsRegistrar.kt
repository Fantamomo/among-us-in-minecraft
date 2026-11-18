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
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * Fills in the `steps` argument of every `createRunner { ... }` call with a `StepInfo` tree of all
 * `step("...")` calls inside the runner lambda, in source order. A `step` call lexically nested in another
 * `step` call becomes a child of it.
 *
 * Every parameter of the `StepInfo` constructor (except `children`) is filled with the argument of the
 * `step` parameter with the same name, so adding a new parameter needs no changes here. Omitted arguments
 * fall back to the default value of the `StepInfo` parameter.
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
private const val STEP = "com.fantamomo.mc.amongus.advancement.utils.step.step"
private const val STEPS_PARAMETER = "steps"
private const val KEY_PARAMETER = "key"
private const val CHILDREN_PARAMETER = "children"

/** A `step(...)` call together with the `step(...)` calls nested inside it. */
private class StepNode(val call: IrCall) {
    val children = mutableListOf<StepNode>()
}

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

            val stepsParameter = function.parameters.firstOrNull { it.name.asString() == STEPS_PARAMETER }
                ?: return expression.also { report(it, "createRunner has no '$STEPS_PARAMETER' parameter") }
            // explicitly passed by the caller, leave it alone
            if (expression.arguments[stepsParameter] != null) return expression

            val stepInfoType = (stepsParameter.type as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull
            val constructor = stepInfoType?.classOrNull?.owner?.primaryConstructor
                ?: return expression.also { report(it, "'$STEPS_PARAMETER' of createRunner must be an Array<StepInfo>") }

            val roots = mutableListOf<StepNode>()
            for (argument in expression.arguments) argument?.let { collectSteps(it, roots) }
            checkUniqueKeys(roots)

            val builder = DeclarationIrBuilder(context, expression.symbol, expression.startOffset, expression.endOffset)
            expression.arguments[stepsParameter] = builder.irArrayOf(
                stepInfoType,
                roots.map { builder.buildStepInfo(it, stepInfoType, constructor) },
            )
            return expression
        }

        /**
         * Collects all `step` calls in [root] into [roots], nested `step` calls are added as children of their parent.
         */
        private fun collectSteps(root: IrExpression, roots: MutableList<StepNode>) {
            root.acceptChildrenVoid(object : IrVisitorVoid() {
                var current = roots

                override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)

                override fun visitCall(expression: IrCall) {
                    if (expression.symbol.owner.kotlinFqName.asString() != STEP) return expression.acceptChildrenVoid(this)

                    val node = StepNode(expression)
                    current += node
                    val parent = current
                    current = node.children
                    expression.acceptChildrenVoid(this)
                    current = parent
                }
            })
        }

        private fun checkUniqueKeys(roots: List<StepNode>) {
            val seen = HashSet<String>()
            fun visit(node: StepNode) {
                val key = node.call.argumentOf(KEY_PARAMETER)
                val value = (key as? IrConst)?.value as? String
                if (value != null && !seen.add(value)) report(key, "Duplicate step key '$value', step keys must be unique per runner")
                node.children.forEach(::visit)
            }
            roots.forEach(::visit)
        }

        /**
         * Creates `StepInfo(...)` for [node], mapping each constructor parameter to the `step` parameter with the same name.
         */
        private fun IrBuilderWithScope.buildStepInfo(node: StepNode, stepInfoType: IrType, constructor: IrConstructor): IrExpression {
            val stepFunction = node.call.symbol.owner
            return irCallConstructor(constructor.symbol, emptyList()).apply {
                for (parameter in constructor.parameters) {
                    val name = parameter.name.asString()
                    if (name == CHILDREN_PARAMETER) {
                        arguments[parameter] = irArrayOf(stepInfoType, node.children.map { buildStepInfo(it, stepInfoType, constructor) })
                        continue
                    }

                    val stepParameter = stepFunction.parameters.firstOrNull {
                        it.kind == IrParameterKind.Regular && it.name == parameter.name
                    }
                    if (stepParameter == null) {
                        if (parameter.defaultValue == null) report(node.call, "StepInfo parameter '$name' has no matching parameter in step(...) and no default value")
                        continue
                    }
                    if (!isAssignable(stepParameter.type, parameter.type)) {
                        report(node.call, "step(...) parameter '$name' (${stepParameter.type.render()}) is not compatible with StepInfo parameter '$name' (${parameter.type.render()})")
                        continue
                    }

                    when (val argument = node.call.arguments[stepParameter]) {
                        null -> if (parameter.defaultValue == null) {
                            report(node.call, "step(...) argument '$name' is omitted, but StepInfo parameter '$name' has no default value")
                        }
                        is IrConst -> arguments[parameter] = argument.deepCopyWithSymbols()
                        else -> report(argument, "step(...) argument '$name' must be a compile-time constant")
                    }
                }
            }
        }

        private fun IrBuilderWithScope.irArrayOf(elementType: IrType, elements: List<IrExpression>): IrExpression {
            val builtIns = context.irBuiltIns
            return irCall(builtIns.arrayOf, builtIns.arrayClass.typeWith(elementType), listOf(elementType)).apply {
                arguments[0] = irVararg(elementType, elements)
            }
        }

        private fun isAssignable(from: IrType, to: IrType) =
            from.classifierOrNull == to.classifierOrNull && (!from.isMarkedNullable() || to.isMarkedNullable())

        private fun IrCall.argumentOf(name: String): IrExpression? {
            val parameter: IrValueParameter = symbol.owner.parameters.firstOrNull { it.name.asString() == name } ?: return null
            return arguments[parameter]
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
