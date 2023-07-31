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
    val details: String = NULL_STRING,
    val enumDefinition: KClass<*> = NULL_CLASS::class,
    val rename: String = NULL_STRING,
    val default: String = NULL_STRING,
    val nullable: Boolean = false,
)

data class PropertyData(
    val name: String,
    val type: DataType?,
    val details: String? = null,
    val enumDefinition: EnumReference? = null,
    val default: String? = null,
    val nullable: Boolean = false,
)

fun Property.toPropertyData(typeFactory: TypeFactory): PropertyData =
    PropertyData(
        name = name,
        type = type.takeIf { it != NULL_TYPE },
        details = details.takeIf { it != NULL_STRING },
        enumDefinition =
            when (type) {
                ENUM ->
                    EnumReference(
                        type = typeFactory.getTypeDefinition(this) { enumDefinition }
                            .takeIf { it.qualifiedName != NULL_CLASS::class.qualifiedName }
                            ?: throw IllegalStateException("Enum definition class is not defined for $name"),
                        enumData = typeFactory.getTypeAnnotation(this, EnumDefinition::class) { enumDefinition }
                            ?.toEnumData()
                            ?: throw IllegalStateException("@EnumDefinition is not defined for $name")
                    )
                else -> null
            },
        default = default.takeIf { it != NULL_STRING },
        nullable = nullable,
    )

