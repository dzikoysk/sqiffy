package com.dzikoysk.sqiffy.dsl

sealed interface Expression<R>

sealed interface Condition : Expression<Boolean>

data class ConstExpression<T>(val value: T) : Expression<T>

data class EqualsExpression<T>(val left: Expression<T>, val right: Expression<T>) : Condition

// @LowPriorityInOverloadResolution
// ~ https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/internal/Annotations.kt#L22-L27
infix fun <T> Expression<T>.eq(to: T): Condition = EqualsExpression(this, ConstExpression(to))