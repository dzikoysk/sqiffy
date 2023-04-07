package com.dzikoysk.sqiffy.dsl

sealed interface Expression<R>

sealed interface Condition : Expression<Boolean>

class ConstExpression<T>(val value: T) : Expression<T>

/* Logical operators */

enum class LogicalOperator(val symbol: String) {
    AND("AND"),
    OR("OR")
}

class LogicalCondition(val operator: LogicalOperator, val conditions: List<Condition>) : Condition
fun and(vararg conditions: Condition): LogicalCondition = LogicalCondition(LogicalOperator.AND, conditions.toList())
fun or(vararg conditions: Condition): LogicalCondition = LogicalCondition(LogicalOperator.OR, conditions.toList())

data class NotExpression(val condition: Condition) : Condition
fun not(condition: Condition): Condition = NotExpression(condition)

/* Basic comparison operators */

enum class ComparisonOperator(val symbol: String) {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUALS(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUALS("<="),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE")
}

class ComparisonCondition<T>(val operator: ComparisonOperator, val left: Expression<T>, val right: Expression<T>) : Condition

// Infix operators may need this annotation in the future:
// @LowPriorityInOverloadResolution
// ~ https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/internal/Annotations.kt#L22-L27

infix fun <T> Expression<T>.eq(to: T): ComparisonCondition<T> = ComparisonCondition(ComparisonOperator.EQUALS, this, ConstExpression(to))
infix fun <T> Expression<T>.notEq(to: T): ComparisonCondition<T> = ComparisonCondition(ComparisonOperator.NOT_EQUALS, this, ConstExpression(to))
infix fun <T> Expression<T>.greaterThan(to: T): ComparisonCondition<T> = ComparisonCondition(ComparisonOperator.GREATER_THAN, this, ConstExpression(to))
infix fun <T> Expression<T>.greaterThanOrEq(to: T): ComparisonCondition<T> = ComparisonCondition(ComparisonOperator.GREATER_THAN_OR_EQUALS, this, ConstExpression(to))
infix fun <T> Expression<T>.lessThan(to: T): ComparisonCondition<T> = ComparisonCondition(ComparisonOperator.LESS_THAN, this, ConstExpression(to))
infix fun <T> Expression<T>.lessThanOrEq(to: T): ComparisonCondition<T> = ComparisonCondition(ComparisonOperator.LESS_THAN_OR_EQUALS, this, ConstExpression(to))
infix fun Expression<String>.like(to: String): ComparisonCondition<String> = ComparisonCondition(ComparisonOperator.LIKE, this, ConstExpression(to))
infix fun Expression<String>.notLike(to: String): ComparisonCondition<String> = ComparisonCondition(ComparisonOperator.NOT_LIKE, this, ConstExpression(to))

/* Complex operators */

//data class BetweenExpression<T>(val value: Expression<T>, val from: Expression<T>, val to: Expression<T>) : Condition
//fun <T> Expression<T>.between(from: T, to: T): Condition = BetweenExpression(this, ConstExpression(from), ConstExpression(to))
//
//data class NotBetweenExpression<T>(val value: Expression<T>, val from: Expression<T>, val to: Expression<T>) : Condition
//fun <T> Expression<T>.notBetween(from: T, to: T): Condition = NotBetweenExpression(this, ConstExpression(from), ConstExpression(to))

