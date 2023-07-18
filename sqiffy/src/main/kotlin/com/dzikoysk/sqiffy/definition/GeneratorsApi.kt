package com.dzikoysk.sqiffy.definition

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class DefinitionEntry(
    val source: String,
    val packageName: String,
    val name: String,
    val definition: Definition,
) {

    fun getDomainPackage(): String =
        definition.domainPackage
            .takeIf { it != NULL_STRING }
            ?: packageName

    fun getInfrastructurePackage(): String =
        definition.infrastructurePackage
            .takeIf { it != NULL_STRING }
            ?: packageName

}

data class TypeDefinition(
    val packageName: String? = null,
    val simpleName: String
) {
    val qualifiedName: String =
        packageName
            ?.let { "$it.$simpleName" }
            ?: simpleName
}

fun KClass<*>.toTypeDefinition(): TypeDefinition =
    TypeDefinition(
        packageName = qualifiedName?.substringBeforeLast("."),
        simpleName = simpleName!!
    )

interface TypeFactory {

    fun <A : Annotation> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition

    fun <A : Annotation, R : Annotation> getTypeAnnotation(annotation: A, annotationType: KClass<R>, supplier: A.() -> KClass<*>): R?

    fun <A : Annotation> getEnumValues(annotation: A, supplier: A.() -> KClass<*>): List<String>?

}

class RuntimeTypeFactory : TypeFactory {

    override fun <A : Annotation> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition =
        supplier.invoke(annotation).toTypeDefinition()

    override fun <A : Annotation, R : Annotation> getTypeAnnotation(annotation: A, annotationType: KClass<R>, supplier: A.() -> KClass<*>): R? =
        supplier.invoke(annotation).findAnnotations(annotationType).firstOrNull()

    override fun <A : Annotation> getEnumValues(annotation: A, supplier: A.() -> KClass<*>): List<String>? =
        supplier.invoke(annotation)
            .takeIf { it.java.isEnum }
            ?.java
            ?.enumConstants
            ?.filterIsInstance<Enum<*>>()
            ?.map { it.name }

}