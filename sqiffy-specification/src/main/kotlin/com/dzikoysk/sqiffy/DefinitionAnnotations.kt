package com.dzikoysk.sqiffy

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
    UUID_BINARY(UUID::class),
    @Deprecated("Might be removed")
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
