package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.PropertyData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass

abstract class Table(name: String) {

    private val tableName: String = name
    fun getName(): String = tableName

    private val columns: MutableList<Column<*>> = mutableListOf()
    fun getColumns(): List<Column<*>> = columns

    protected fun <T : Any> column(name: String, dataType: DataType, dbType: String, type: KClass<T>): Column<T> =
        Column.of(this, name, dataType, dbType, type)
            .also { columns.add(it) }

    protected fun serial(name: String, dbType: String): Column<Int> = column(name, DataType.SERIAL, dbType, Int::class)

    protected fun char(name: String, dbType: String): Column<Char> = column(name, DataType.CHAR, dbType, Char::class)
    protected fun varchar(name: String,  dbType: String): Column<String> = column(name, DataType.VARCHAR, dbType, String::class)
    protected fun text(name: String, dbType: String): Column<String> = column(name, DataType.TEXT, dbType, String::class)

    protected fun bool(name: String, dbType: String): Column<Boolean> = column(name, DataType.BOOLEAN, dbType, Boolean::class)
    protected fun <E : Enum<E>> enumeration(name: String, dbType: String, type: KClass<E>): Column<E> = column(name, DataType.ENUM, dbType, type)

    protected fun binary(name: String, dbType: String): Column<ByteArray> = column(name, DataType.BINARY, dbType, ByteArray::class)
    protected fun uuid(name: String, dbType: String): Column<UUID> = column(name, DataType.UUID_TYPE, dbType, UUID::class)

    protected fun integer(name: String, dbType: String): Column<Int> = column(name, DataType.INT, dbType, Int::class)
    protected fun long(name: String, dbType: String): Column<Long> = column(name, DataType.LONG, dbType, Long::class)
    protected fun float(name: String, dbType: String): Column<Float> = column(name, DataType.FLOAT, dbType, Float::class)
    protected fun double(name: String, dbType: String): Column<Double> = column(name, DataType.DOUBLE, dbType, Double::class)

    protected fun date(name: String, dbType: String): Column<LocalDate> = column(name, DataType.DATE, dbType, LocalDate::class)
    protected fun datetime(name: String, dbType: String): Column<LocalDateTime> = column(name, DataType.DATETIME, dbType, LocalDateTime::class)
    protected fun timestamp(name: String, dbType: String): Column<Instant> = column(name, DataType.TIMESTAMP, dbType, Instant::class)

    override fun toString(): String = "table ${getName()}"

}

data class Column<T>(
    val table: Table,
    val name: String,
    val dataType: DataType,
    val dbType: String,
    val type: Class<T>,
    val nullable: Boolean = false
) : Expression<Column<T>, T>, Selectable {

    companion object {
        fun <T : Any> of(table: Table, name: String, dataType: DataType, dbType: String, type: KClass<T>): Column<T> = Column(table, name, dataType, dbType, type.javaObjectType)
    }

    override val selectableType: SelectableType = SelectableType.COLUMN

    val rawIdentifier: String = "${table.getName()}.$name"
    val quotedIdentifier = Quoted { "${it.quote(table.getName())}.${it.quote(name)}" }

    @Suppress("UNCHECKED_CAST")
    fun nullable(): Column<T?> = copy(nullable = true) as Column<T?>

    fun getPropertyData(): PropertyData =
        PropertyData(
            name = name,
            type = dataType,
            nullable = nullable,
        )

}