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

data class NotCondition(val condition: Condition) : Condition
fun not(condition: Condition): NotCondition = NotCondition(condition)

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

data class Between<T>(val from: Expression<T>, val to: Expression<T>)
infix fun <T> T.and(to: T): Between<T> = Between(ConstExpression(this), ConstExpression(to))

class BetweenCondition<T>(val value: Expression<T>, val between: Between<T>) : Condition
fun <T> Expression<T>.between(from: T, to: T): BetweenCondition<T> = this between (from and to)
fun <T> Expression<T>.notBetween(from: T, to: T): NotCondition = this notBetween (from and to)

infix fun <T> Expression<T>.between(between: Between<T>): BetweenCondition<T> = BetweenCondition(this, between)
infix fun <T> Expression<T>.notBetween(between: Between<T>): NotCondition = NotCondition(BetweenCondition(this, between))
