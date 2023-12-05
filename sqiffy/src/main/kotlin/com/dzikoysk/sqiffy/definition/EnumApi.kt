package com.dzikoysk.sqiffy.definition

import kotlin.annotation.AnnotationTarget.CLASS

@Target(CLASS)
annotation class EnumDefinition(
    val name: String = NULL_STRING,
    val mappedTo: String,
    val value: Array<EnumVersion>
)

@Target(CLASS)
annotation class RawEnum

enum class EnumOperation {
    ADD_VALUES
}

@Target()
annotation class EnumVersion(
    val version: String,
    val operation: EnumOperation,
    val values: Array<String>,
)

fun EnumDefinition.toEnumData(): EnumDefinitionData =
    EnumDefinitionData(
        name =
            name
                .takeIf { it != NULL_STRING }
                ?: throw IllegalStateException("Enum name cannot be null"),
        mappedTo =
            mappedTo
                .takeIf { it != NULL_STRING }
                ?: throw IllegalStateException("Enum name cannot be null"),
        versions =
            value.map {
                EnumVersionData(
                    version = it.version,
                    operation = it.operation,
                    values = it.values.toList()
                )
            }
    )

data class EnumDefinitionData(
    val name: String,
    val mappedTo: String,
    val versions: List<EnumVersionData>
) {

    fun getMappedTypeDefinition(): TypeDefinition =
        TypeDefinition(
            packageName = mappedTo.substringBeforeLast("."),
            simpleName = mappedTo.substringAfterLast(".")
        )

}

data class EnumVersionData(
    val version: String,
    val operation: EnumOperation,
    val values: List<String>,
)