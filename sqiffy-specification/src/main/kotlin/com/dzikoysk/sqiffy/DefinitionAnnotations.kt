package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.DataType.NULL_TYPE
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass

const val NULL_STRING = "~NULL-STRING~"
object NULL_CLASS

enum class DataType(val javaType: KClass<*>) {
    NULL_TYPE(NULL_CLASS::class),
    CHAR(Char::class),
    UUID_VARCHAR(UUID::class),
    VARCHAR(String::class),
    BINARY(ByteArray::class),
    TEXT(String::class),
    BLOB(ByteArray::class),
    BOOLEAN(Boolean::class),
    INT(Int::class),
    FLOAT(Float::class),
    DOUBLE(Double::class),
    DATE(LocalDate::class),
    DATETIME(LocalDateTime::class),
    TIMESTAMP(Instant::class)
}

enum class ConstraintType {
    PRIMARY_KEY,
    FOREIGN_KEY
}

enum class IndexType {
    INDEX,
    UNIQUE_INDEX
}

@Retention(RUNTIME)
annotation class Definition(
    val value: Array<DefinitionVersion>
)

@Target()
annotation class DefinitionVersion(
    val version: String,
    val name: String = NULL_STRING,
    val properties: Array<Property> = [],
    val constraints: Array<Constraint> = [],
    val indices: Array<Index> = []
)

enum class PropertyDefinitionType {
    ADD,
    RENAME,
    RETYPE,
    REMOVE
}

@Target()
annotation class Property(
    val definitionType: PropertyDefinitionType = PropertyDefinitionType.ADD,
    val name: String,
    val type: DataType = NULL_TYPE,
    val details: String = NULL_STRING,
    val rename: String = NULL_STRING,
    val retypeType: DataType = NULL_TYPE,
    val retypeDetails: String = NULL_STRING,
    val nullable: Boolean = false,
    val autoincrement: Boolean = false
)

data class PropertyData(
    val name: String,
    val type: DataType?,
    val details: String?,
    val nullable: Boolean,
    val autoIncrement: Boolean,
)

fun Property.toPropertyData(): PropertyData =
    PropertyData(
        name = name,
        type = type.takeIf { it != NULL_TYPE },
        details = details.takeIf { it != NULL_STRING },
        nullable = nullable,
        autoIncrement = autoincrement
    )

@Target()
annotation class Constraint(
    val value: ConstraintType,
    val on: String,
    val referenced: KClass<*> = NULL_CLASS::class,
    val references: String = NULL_STRING
)

@Target()
annotation class Index(
    val value: IndexType,
    val columns: Array<String>
)