package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.Kind.DIRECT
import com.dzikoysk.sqiffy.definition.Kind.INDIRECT
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass

object NULL_CLASS
const val NULL_STRING = "~NULL-STRING~"
const val NULL_VALUE = "~NULL-VALUE~"

enum class Kind {
    DIRECT,
    INDIRECT
}

enum class DataType(
    val kind: Kind,
    val mappedTo: KClass<*>,
    val contextualType: (PropertyData) -> TypeDefinition = { it.mappedTo ?: mappedTo.toTypeDefinition() }
) {
    /* Special types */
    NULL_TYPE(INDIRECT, NULL_CLASS::class),
    UUID_TYPE(INDIRECT, UUID::class),
    SERIAL(INDIRECT, Int::class),
    ENUM(DIRECT, Enum::class, { it.enumDefinition?.getMappedTypeDefinition() ?: throw IllegalStateException("Enum definition class is not defined for $it") }),

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
    NUMERIC(DIRECT, BigDecimal::class),
    DECIMAL(DIRECT, BigDecimal::class),
    DATE(DIRECT, LocalDate::class),
    DATETIME(DIRECT, LocalDateTime::class),
    TIMESTAMP(DIRECT, Instant::class)
}

@Retention(RUNTIME)
annotation class Definition(
    val versions: Array<DefinitionVersion>,
    /** Package for domain related classes */
    val domainPackage: String = NULL_STRING,
    /** Package for database related classes */
    val infrastructurePackage: String = NULL_STRING,
    /** Package for API related classes (e.g. DTOs) */
    val apiPackage: String = NULL_STRING,
)

data class DefinitionData(
    val versions: List<DefinitionVersionData>,
    val domainPackage: String?,
    val infrastructurePackage: String?,
    val apiPackage: String?,
)

fun Definition.toData(): DefinitionData =
    DefinitionData(
        versions = versions.map { it.toData() },
        domainPackage = domainPackage.takeIf { it != NULL_STRING },
        infrastructurePackage = infrastructurePackage.takeIf { it != NULL_STRING },
        apiPackage = apiPackage.takeIf { it != NULL_STRING }
    )

@Target()
annotation class DefinitionVersion(
    val version: String,
    val name: String = NULL_STRING,
    val properties: Array<Property> = [],
    val constraints: Array<Constraint> = [],
    val indices: Array<Index> = [],
)

fun DefinitionVersion.toData(): DefinitionVersionData =
    DefinitionVersionData(
        version = version,
        name = name.takeIf { it != NULL_STRING },
        properties = properties.toList(),
        constraints = constraints.toList(),
        indices = indices.toList()
    )

data class DefinitionVersionData(
    val version: String,
    val name: String?,
    val properties: List<Property>,
    val constraints: List<Constraint>,
    val indices: List<Index>,
)
