package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.shared.toQuoted
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

    protected fun <T : Any> column(name: String, dbType: String, type: KClass<T>): Column<T> =
        Column.of(this, name, dbType, type)
            .also { columns.add(it) }

    protected fun char(name: String, dbType: String): Column<Char> = column(name, dbType, Char::class)
    protected fun varchar(name: String,  dbType: String, length: Int): Column<String> = column(name,  dbType, String::class)
    protected fun text(name: String, dbType: String): Column<String> = column(name,  dbType, String::class)

    protected fun bool(name: String, dbType: String): Column<Boolean> = column(name,  dbType, Boolean::class)
    protected fun <E : Enum<E>> enumeration(name: String, dbType: String, type: KClass<E>): Column<E> = column(name,  dbType, type)

    protected fun binary(name: String, dbType: String): Column<ByteArray> = column(name,  dbType, ByteArray::class)
    protected fun uuid(name: String, dbType: String): Column<UUID> = column(name,  dbType, UUID::class)

    protected fun integer(name: String, dbType: String): Column<Int> = column(name,  dbType, Int::class)
    protected fun long(name: String, dbType: String): Column<Long> = column(name,  dbType, Long::class)
    protected fun float(name: String, dbType: String): Column<Float> = column(name,  dbType, Float::class)
    protected fun double(name: String, dbType: String): Column<Double> = column(name,  dbType, Double::class)

    protected fun date(name: String, dbType: String): Column<LocalDate> = column(name,  dbType, LocalDate::class)
    protected fun datetime(name: String, dbType: String): Column<LocalDateTime> = column(name,  dbType, LocalDateTime::class)
    protected fun timestamp(name: String, dbType: String): Column<Instant> = column(name,  dbType, Instant::class)

}

data class Column<T>(
    val table: Table,
    val name: String,
    val dbType: String,
    val type: Class<T>,
    val nullable: Boolean = false
) : Expression<T> {

    companion object {
        fun <T : Any> of(table: Table, name: String, dbType: String, type: KClass<T>): Column<T> = Column(table, name, dbType, type.javaObjectType)
    }

    @Suppress("UNCHECKED_CAST")
    fun nullable(): Column<T?> = copy(nullable = true) as Column<T?>

    fun toQuotedIdentifier(): String = "${table.getTableName().toQuoted()}.${name.toQuoted()}"

}