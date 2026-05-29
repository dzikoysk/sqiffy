package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.EnumOperation.ADD_VALUES
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

@Target(CLASS)
annotation class EnumDefinition(
    val name: String = NULL_STRING,
    /**
     * Fully-qualified name of the enum to generate. Mutually exclusive with [mappedFrom]. If an enum
     * already exists at this name it is referenced (and validated) instead of generated.
     */
    val mappedTo: String = NULL_STRING,
    val value: Array<EnumVersion>,
    /**
     * An existing enum to map onto, as a class reference instead of a string [mappedTo]. The enum is
     * referenced (never generated) and its constants are validated against [value] at compile time.
     * Declared after [value] so existing definitions that pass `value` positionally keep binding correctly.
     */
    val mappedFrom: KClass<*> = NULL_CLASS::class,
)

@Target(CLASS)
annotation class RawEnum(
    val name: String
)

enum class EnumOperation {
    ADD_VALUES
}

@Target()
annotation class EnumVersion(
    val version: String,
    val operation: EnumOperation = ADD_VALUES,
    val values: Array<String>,
)

/** [mappedFrom] is the resolved type of the `mappedFrom` class reference (null when only `mappedTo` is set). */
fun EnumDefinition.toEnumData(mappedFrom: TypeDefinition?): EnumDefinitionData =
    EnumDefinitionData(
        name =
            name
                .takeIf { it != NULL_STRING }
                ?: throw IllegalStateException("Enum name cannot be null"),
        raw =
            false,
        mappedTo =
            mappedFrom?.qualifiedName
                ?: mappedTo.takeIf { it != NULL_STRING }
                ?: throw IllegalStateException("@EnumDefinition '$name' must declare either mappedTo or mappedFrom"),
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
    val raw: Boolean,
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