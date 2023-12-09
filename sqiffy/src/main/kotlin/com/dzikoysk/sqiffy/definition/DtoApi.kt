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

enum class Mode {
    INCLUDE,
    EXCLUDE
}

@Target(ANNOTATION_CLASS)
annotation class Variant(
    val name: String,
    val mode: Mode = Mode.INCLUDE,
    val properties: Array<String> = [],
    val implements: Array<KClass<*>> = []
)

fun Variant.toVariantData(typeFactory: TypeFactory): VariantData =
    VariantData(
        name = name,
        mode = mode,
        properties = properties.toList(),
        implements = implements.map { typeFactory.getTypeDefinition(this) { it } }
    )

data class VariantData(
    val name: String,
    val mode: Mode,
    val properties: List<String>,
    val implements: List<TypeDefinition>
)