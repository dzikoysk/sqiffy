package com.dzikoysk.sqiffy.definition

import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

@Target(CLASS)
annotation class DtoDefinition(
    val from: KClass<*>,
    val variants: Array<Variant>
)

data class DtoGroupData(
    val from: TypeDefinition,
    val variants: List<VariantData>
)

fun DtoDefinition.toDtoDefinitionData(typeFactory: TypeFactory, namingStrategy: NamingStrategy): DtoGroupData =
    DtoGroupData(
        from = typeFactory.getTypeDefinition(this) { from },
        variants = variants.map { it.toVariantData(typeFactory, namingStrategy) }
    )

enum class Mode {
    INCLUDE,
    EXCLUDE
}

@Target(ANNOTATION_CLASS)
annotation class Variant(
    val name: String,
    val mode: Mode = Mode.INCLUDE,
    val properties: Array<String> = [],
    val additionalProperties: Array<AdditionalProperty> = [],
    val implements: Array<KClass<*>> = []
)

@Target(ANNOTATION_CLASS)
annotation class AdditionalProperty(
    val name: String,
    val type: KClass<*>,
    val nullable: Boolean = false,
)

fun Variant.toVariantData(typeFactory: TypeFactory, namingStrategy: NamingStrategy): VariantData =
    VariantData(
        name = name,
        mode = mode,
        properties = properties.map {
            PropertyReference(
                name = it,
                formattedProperty = NamingStrategyFormatter.format(namingStrategy, it)
            )
        },
        additionalProperties = additionalProperties.map {
            AdditionalPropertyData(
                name = it.name,
                formattedName = NamingStrategyFormatter.format(namingStrategy, it.name),
                type = typeFactory.getTypeDefinition(this) { it.type },
                nullable = it.nullable
            )
        },
        implements = implements.map { typeFactory.getTypeDefinition(this) { it } }
    )

data class VariantData(
    val name: String,
    val mode: Mode,
    val properties: List<PropertyReference>,
    val additionalProperties: List<AdditionalPropertyData>,
    val implements: List<TypeDefinition>
)

data class AdditionalPropertyData(
    val name: String,
    val formattedName: String,
    val type: TypeDefinition,
    val nullable: Boolean,
)

data class PropertyReference(
    val name: String,
    val formattedProperty: String
)