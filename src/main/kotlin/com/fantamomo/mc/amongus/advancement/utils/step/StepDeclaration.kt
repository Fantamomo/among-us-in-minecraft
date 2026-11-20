package com.fantamomo.mc.amongus.advancement.utils.step

/**
 * Marks a function as a step declaration for the `advancement-compiler-plugin`.
 *
 * Every call of such a function inside `createRunner { ... }` becomes a [StepInfo]. The function must have a
 * `key: String` parameter, all other parameters are mapped to the [StepInfo] constructor by name.
 *
 * @see step
 * @see com.fantamomo.mc.amongus.advancement.AllOfScope.branch
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class StepDeclaration

/**
 * Marks a function that runs branches in parallel for the `advancement-compiler-plugin`.
 *
 * The steps declared directly inside a call of such a function (usually the named branches) get the same
 * [StepInfo.groupId], so the GUI can show them as `2a.`, `2b.`, ...
 *
 * @param requireAll true if all branches must complete ([com.fantamomo.mc.amongus.advancement.AdvancementSequence.allOf]),
 *                   false if one is enough ([com.fantamomo.mc.amongus.advancement.AdvancementSequence.race])
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class StepGroupDeclaration(val requireAll: Boolean)
