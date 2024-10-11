//@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

@file:Suppress("unused")

package com.dzikoysk.sqiffy.dsl

//import kotlin.internal.LowPriorityInOverloadResolution

sealed interface Expression<SOURCE, RESULT>

sealed interface Condition<SOURCE> : Expression<SOURCE, Boolean>

class ConstExpression<T>(val value: T) : Expression<T, T>

/* Math operators */

enum class MathOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%")
}

class MathExpression<SOURCE, VALUE : Number>(
    val operator: MathOperator,
    val left: Expression<*, VALUE>,
    val right: Expression<*, VALUE>
) : Expression<SOURCE, VALUE>

operator fun <SOURCE, VALUE : Number> Expression<SOURCE, VALUE>.plus(value: VALUE): MathExpression<SOURCE, VALUE> = MathExpression(MathOperator.ADD, this, ConstExpression(value))
operator fun <SOURCE, VALUE : Number> Expression<SOURCE, VALUE>.minus(value: VALUE): MathExpression<SOURCE, VALUE> = MathExpression(MathOperator.SUBTRACT, this, ConstExpression(value))
operator fun <SOURCE, VALUE : Number> Expression<SOURCE, VALUE>.times(value: VALUE): MathExpression<SOURCE, VALUE> = MathExpression(MathOperator.MULTIPLY, this, ConstExpression(value))
operator fun <SOURCE, VALUE : Number> Expression<SOURCE, VALUE>.div(value: VALUE): MathExpression<SOURCE, VALUE> = MathExpression(MathOperator.DIVIDE, this, ConstExpression(value))
operator fun <SOURCE, VALUE : Number> Expression<SOURCE, VALUE>.rem(value: VALUE): MathExpression<SOURCE, VALUE> = MathExpression(MathOperator.MODULO, this, ConstExpression(value))

/* Logical operators */

enum class LogicalOperator(val symbol: String) {
    AND("AND"),
    OR("OR")
}

class LogicalCondition<SOURCE>(val operator: LogicalOperator, val conditions: List<Condition<out SOURCE>>) : Condition<SOURCE>

fun <SOURCE> and(vararg conditions: Condition<out SOURCE>): LogicalCondition<SOURCE> = LogicalCondition(LogicalOperator.AND, conditions.toList())
infix fun <SOURCE> Condition<out SOURCE>.and(value: Condition<out SOURCE>): LogicalCondition<SOURCE> = and(this, value)

fun <SOURCE> or(vararg conditions: Condition<out SOURCE>): LogicalCondition<SOURCE> = LogicalCondition(LogicalOperator.OR, conditions.toList())
infix fun <SOURCE> Condition<out SOURCE>.or(value: Condition<out SOURCE>): LogicalCondition<SOURCE> = or(this, value)

data class NotCondition<SOURCE>(val condition: Condition<SOURCE>) : Condition<SOURCE>
fun <SOURCE> not(condition: Condition<SOURCE>): NotCondition<SOURCE> = NotCondition(condition)

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

class ComparisonCondition<SOURCE, RESULT>(
    val operator: ComparisonOperator,
    val left: Expression<*, RESULT>,
    val right: Expression<*, RESULT>
) : Condition<SOURCE>

infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.eq(to: Expression<SOURCE, RESULT>): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.EQUALS, this, to)
infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.eq(to: RESULT): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.EQUALS, this, ConstExpression(to))

infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.notEq(to: Expression<SOURCE, RESULT>): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.NOT_EQUALS, this, to)
infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.notEq(to: RESULT): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.NOT_EQUALS, this, ConstExpression(to))

infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.greaterThan(to: Expression<SOURCE, RESULT>): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.GREATER_THAN, this, to)
infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.greaterThan(to: RESULT): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.GREATER_THAN, this, ConstExpression(to))

infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.greaterThanOrEq(to: Expression<SOURCE, RESULT>): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.GREATER_THAN_OR_EQUALS, this, to)
infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.greaterThanOrEq(to: RESULT): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.GREATER_THAN_OR_EQUALS, this, ConstExpression(to))

infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.lessThan(to: Expression<SOURCE, RESULT>): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.LESS_THAN, this, to)
infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.lessThan(to: RESULT): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.LESS_THAN, this, ConstExpression(to))

infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.lessThanOrEq(to: Expression<SOURCE, RESULT>): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.LESS_THAN_OR_EQUALS, this, to)
infix fun <SOURCE, RESULT> Expression<SOURCE, RESULT>.lessThanOrEq(to: RESULT): ComparisonCondition<SOURCE, RESULT> = ComparisonCondition(ComparisonOperator.LESS_THAN_OR_EQUALS, this, ConstExpression(to))

infix fun <SOURCE> Expression<SOURCE, String>.like(to: Expression<SOURCE, String>): ComparisonCondition<SOURCE, String> = ComparisonCondition(ComparisonOperator.LIKE, this, to)
infix fun <SOURCE> Expression<SOURCE, String>.like(to: String): ComparisonCondition<SOURCE, String> = ComparisonCondition(ComparisonOperator.LIKE, this, ConstExpression(to))

infix fun <SOURCE> Expression<SOURCE, String>.notLike(to: Expression<SOURCE, String>): ComparisonCondition<SOURCE, String> = ComparisonCondition(ComparisonOperator.NOT_LIKE, this, to)
infix fun <SOURCE> Expression<SOURCE, String>.notLike(to: String): ComparisonCondition<SOURCE, String> = ComparisonCondition(ComparisonOperator.NOT_LIKE, this, ConstExpression(to))

/* Complex operators */

data class Between<RESULT>(val from: Expression<*, RESULT>, val to: Expression<*, RESULT>)
infix fun <T> T.and(to: T): Between<T> = Between(ConstExpression(this), ConstExpression(to))

class BetweenCondition<SOURCE, T>(val value: Expression<SOURCE, T>, val between: Between<T>) : Condition<SOURCE>
fun <SOURCE, T> Expression<SOURCE, T>.between(from: T, to: T): BetweenCondition<SOURCE, T> = this between (from and to)
fun <SOURCE, T> Expression<SOURCE, T>.notBetween(from: T, to: T): NotCondition<SOURCE> = this notBetween (from and to)

infix fun <SOURCE, T> Expression<SOURCE, T>.between(between: Between<T>): BetweenCondition<SOURCE, T> = BetweenCondition(this, between)
infix fun <SOURCE, T> Expression<SOURCE, T>.notBetween(between: Between<T>): NotCondition<SOURCE> = NotCondition(BetweenCondition(this, between))

enum class WithinType {
    IN, NOT_IN
}

data class WithinCondition<SOURCE, T>(val type: WithinType, val value: Expression<SOURCE, T>, val values: Collection<Expression<*, T>>) : Condition<SOURCE>
//infix fun <SOURCE, T> Expression<SOURCE, T>.within(values: Collection<Expression<SOURCE, T>>): InCondition<SOURCE, T> = InCondition(this, values)
infix fun <SOURCE, T> Expression<SOURCE, T>.within(values: Collection<T>): WithinCondition<SOURCE, T> = WithinCondition(WithinType.IN, this, values.map { ConstExpression(it) })
//infix fun <SOURCE, T> Expression<SOURCE, T>.notWithin(values: Collection<Expression<SOURCE, T>>): NotCondition<SOURCE> = NotCondition(InCondition(this, values))
infix fun <SOURCE, T> Expression<SOURCE, T>.notWithin(values: Collection<T>): WithinCondition<SOURCE, T> = WithinCondition(WithinType.NOT_IN, this, values.map { ConstExpression(it) })