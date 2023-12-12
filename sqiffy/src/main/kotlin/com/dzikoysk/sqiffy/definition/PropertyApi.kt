package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.NULL_TYPE
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD
import kotlin.reflect.KClass

enum class PropertyDefinitionOperation {
    ADD,
    RENAME,
    RETYPE,
    REMOVE,
}

@Target()
annotation class Property(
    val operation: PropertyDefinitionOperation = ADD,
    val name: String,
    val type: DataType = NULL_TYPE,
    val mappedTo: KClass<*> = NULL_CLASS::class,
    val details: String = NULL_STRING,
    val enumDefinition: KClass<*> = NULL_CLASS::class,
    val rename: String = NULL_STRING,
    val default: String = NULL_STRING,
    val rawDefault: Boolean = false,
    val nullable: Boolean = false,
)

data class PropertyData(
    val name: String,
    val formattedName: String,
    val type: DataType?,
    val mappedTo: TypeDefinition?,
    val details: String?,
    val enumDefinition: EnumDefinitionData?,
    val default: String?,
    val rawDefault: Boolean,
    val nullable: Boolean,
)

fun Property.toPropertyData(typeFactory: TypeFactory, namingStrategy: NamingStrategy): PropertyData =
    PropertyData(
        name = name,
        formattedName = NamingStrategyFormatter.format(namingStrategy, name),
        type = type.takeIf { it != NULL_TYPE },
        mappedTo = typeFactory.getTypeDefinition(this) { mappedTo }.takeIf { it.qualifiedName != NULL_CLASS::class.qualifiedName },
        details = details.takeIf { it != NULL_STRING },
        enumDefinition =
            when (type) {
                ENUM ->
                    typeFactory.getTypeAnnotation(this, EnumDefinition::class) { enumDefinition }
                        ?.toEnumData()
                        ?: run {
                            when {
                                typeFactory.getTypeAnnotation(this, RawEnum::class) { enumDefinition } != null ->
                                    EnumDefinitionData(
                                        name = name,
                                        raw = true,
                                        mappedTo = typeFactory.getTypeDefinition(this) { enumDefinition }.qualifiedName,
                                        versions = listOf(
                                            EnumVersionData(
                                                version = "0.0.0",
                                                operation = EnumOperation.ADD_VALUES,
                                                values = typeFactory.getEnumValues(this) { enumDefinition }?.toList() ?: emptyList()
                                            )
                                        )
                                    )
                                else -> throw IllegalStateException("@EnumDefinition is not defined for $name")
                            }
                        }
                else -> null
            },
        default = default.takeIf { it != NULL_STRING },
        rawDefault = rawDefault,
        nullable = nullable,
    )