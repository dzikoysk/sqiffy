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

fun DtoDefinition.toDtoDefinitionData(typeFactory: TypeFactory): DtoGroupData =
    DtoGroupData(
        from = typeFactory.getTypeDefinition(this) { from },
        variants = variants.map { it.toVariantData(typeFactory) }
    )

@Target(ANNOTATION_CLASS)
annotation class Variant(
    val name: String,
    val properties: Array<String> = [],
    val allProperties: Boolean = false,
    val implements: Array<KClass<*>> = []
)

fun Variant.toVariantData(typeFactory: TypeFactory): VariantData =
    VariantData(
        name = name,
        properties = properties.toList(),
        allProperties = allProperties,
        implements = implements.map { typeFactory.getTypeDefinition(this) { it } }
    )

data class VariantData(
    val name: String,
    val properties: List<String>,
    val allProperties: Boolean,
    val implements: List<TypeDefinition>
)