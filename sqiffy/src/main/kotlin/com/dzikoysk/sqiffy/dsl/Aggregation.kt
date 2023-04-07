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
    val column: Column<*>,
) : Selectable {

    override val selectableType: SelectableType = SelectableType.AGGREGATION

    fun getAggregationFunction(): String = type.aggregationFunction
    fun getTableName(): String = column.table.getTableName()
    fun getColumnName(): String = column.name

}

fun <T> Column<T>.count(): Aggregation<Long> = Aggregation(AggregationType.COUNT, Long::class.javaObjectType, this)
fun <N : Number> Column<N>.sum(): Aggregation<Long> = Aggregation(AggregationType.SUM, Long::class.javaObjectType, this)
fun <N : Number> Column<N>.avg(): Aggregation<Double> = Aggregation(AggregationType.AVG, Double::class.javaObjectType, this)
fun <T> Column<T>.min(): Aggregation<T> = Aggregation(AggregationType.MIN, type, this)
fun <T> Column<T>.max(): Aggregation<T> = Aggregation(AggregationType.MAX, type, this)
