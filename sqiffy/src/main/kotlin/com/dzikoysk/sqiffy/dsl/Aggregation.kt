package com.dzikoysk.sqiffy.dsl

enum class AggregationType(val aggregationFunction: String) {
    COUNT("COUNT"),
    SUM("SUM"),
    AVG("AVG"),
    MIN("MIN"),
    MAX("MAX")
}

data class Aggregation<T>(
    val type: AggregationType,
    val resultType: Class<T>,
    val rawIdentifier: String,
    val quotedIdentifier: Quoted,
    val fallbackAlias: String = rawIdentifier // fallback for generated alias
) : Selectable, Expression<Aggregation<T>, T> {
    override val selectableType: SelectableType = SelectableType.AGGREGATION
    val aggregationFunction: String = type.aggregationFunction
}

fun Table.count(): Aggregation<Long> = Aggregation(AggregationType.COUNT, Long::class.javaObjectType, "*", { "*" })
fun <T> Column<T>.count(): Aggregation<Long> = Aggregation(AggregationType.COUNT, Long::class.javaObjectType, rawIdentifier, quotedIdentifier, name)
fun <N : Number> Column<N>.sum(): Aggregation<Long> = Aggregation(AggregationType.SUM, Long::class.javaObjectType, rawIdentifier, quotedIdentifier, name)
fun <N : Number> Column<N>.avg(): Aggregation<Double> = Aggregation(AggregationType.AVG, Double::class.javaObjectType, rawIdentifier, quotedIdentifier, name)
fun <T> Column<T>.min(): Aggregation<T> = Aggregation(AggregationType.MIN, type, rawIdentifier, quotedIdentifier, name)
fun <T> Column<T>.max(): Aggregation<T> = Aggregation(AggregationType.MAX, type, rawIdentifier, quotedIdentifier, name)
