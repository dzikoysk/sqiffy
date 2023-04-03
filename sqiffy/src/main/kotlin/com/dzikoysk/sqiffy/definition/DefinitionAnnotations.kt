package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.Kind.DIRECT
import com.dzikoysk.sqiffy.definition.Kind.INDIRECT
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass

const val NULL_STRING = "~NULL-STRING~"
object NULL_CLASS

enum class Kind {
    DIRECT,
    INDIRECT
}

enum class DataType(
    val kind: Kind,
    val javaType: KClass<*>,
    val contextualType: (PropertyData) -> TypeDefinition = { javaType.toTypeDefinition() }
) {
    /* Special types */
    NULL_TYPE(INDIRECT, NULL_CLASS::class),
    UUID_TYPE(INDIRECT, UUID::class),
    SERIAL(INDIRECT, Int::class),
    ENUM(DIRECT, Enum::class, { it.enumDefinition?.getEnumClassQualifier() ?: throw IllegalStateException("Enum definition class is not defined for ${it}") }),

    /* Regular types */
    CHAR(DIRECT, Char::class),
    VARCHAR(DIRECT, String::class),
    BINARY(DIRECT, ByteArray::class),
    TEXT(DIRECT, String::class),
    BOOLEAN(DIRECT, Boolean::class),
    INT(DIRECT, Int::class),
    LONG(DIRECT, Long::class),
    FLOAT(DIRECT, Float::class),
    DOUBLE(DIRECT, Double::class),
    DATE(DIRECT, LocalDate::class),
    DATETIME(DIRECT, LocalDateTime::class),
    TIMESTAMP(DIRECT, Instant::class)
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
    val indices: Array<Index> = [],
)
