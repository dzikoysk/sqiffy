package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.SqiffyDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass

abstract class Table(name: String) {

    private val tableName: String = name
    fun getTableName(): String = tableName

    private val columns: MutableList<Column<*>> = mutableListOf()
    fun getColumns(): List<Column<*>> = columns

    protected fun <T : Any> column(name: String, type: KClass<T>): Column<T> =
        Column.of(name, type)
            .also { columns.add(it) }

    protected fun char(name: String): Column<Char> = column(name, Char::class)
    protected fun varchar(name: String, length: Int): Column<String> = column(name, String::class)
    protected fun text(name: String): Column<String> = column(name, String::class)

    protected fun bool(name: String): Column<Boolean> = column(name, Boolean::class)
    protected fun binary(name: String): Column<ByteArray> = column(name, ByteArray::class)
    protected fun uuid(name: String): Column<UUID> = column(name, UUID::class)

    protected fun integer(name: String): Column<Int> = column(name, Int::class)
    protected fun long(name: String): Column<Long> = column(name, Long::class)
    protected fun float(name: String): Column<Float> = column(name, Float::class)
    protected fun double(name: String): Column<Double> = column(name, Double::class)

    protected fun date(name: String): Column<LocalDate> = column(name, LocalDate::class)
    protected fun datetime(name: String): Column<LocalDateTime> = column(name, LocalDateTime::class)
    protected fun timestamp(name: String): Column<Instant> = column(name, Instant::class)

    fun select(database: SqiffyDatabase, where: () -> Expression<Boolean>): SelectBuilder = SelectBuilder(database, this, where.invoke())

}

data class Column<T>(
    val name: String,
    val type: Class<T>,
    val nullable: Boolean = false
) : Expression<T> {

    companion object {
        fun <T : Any> of(name: String, type: KClass<T>): Column<T> = Column(name, type.javaObjectType)
    }

    @Suppress("UNCHECKED_CAST")
    fun nullable(): Column<T?> = copy(nullable = true) as Column<T?>

}