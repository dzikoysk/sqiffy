package com.dzikoysk.sqiffy.definition

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class ParsedDefinition(
    val source: String,
    val packageName: String,
    val name: String,
    val definition: DefinitionData,
    val implements: List<TypeDefinition>,
    val overrideProperties: Set<String> = emptySet(),
) {

    fun getDomainPackage(): String =
        definition.domainPackage
            .takeIf { it != NULL_STRING }
            ?: packageName

    fun getInfrastructurePackage(): String =
        definition.infrastructurePackage
            .takeIf { it != NULL_STRING }
            ?: getDomainPackage()

    fun getApiPackage(): String =
        definition.apiPackage
            .takeIf { it != NULL_STRING }
            ?: getDomainPackage()

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

    fun <A : Annotation?> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition

    fun <A : Annotation?> getTypeDefinitions(annotation: A, supplier: A.() -> Array<KClass<*>>): List<TypeDefinition> =
        supplier.invoke(annotation).map { getTypeDefinition(annotation) { it } }

    fun <A : Annotation?, R : Annotation> getTypeAnnotation(annotation: A, annotationType: KClass<R>, supplier: A.() -> KClass<*>): R?

    fun <A : Annotation?> getEnumValues(annotation: A, supplier: A.() -> KClass<*>): List<String>?

    /**
     * Resolves a single `KClass`-typed [member] of [annotationType] declared on the class produced by
     * [supplier], returning its type (or null when absent / left as `NULL_CLASS`).
     *
     * KSP's `getAnnotationsByType` proxy NPEs on single-`KClass` members of annotations that also have an
     * array member (it routes them through `asArray`), so the KSP factory reads the value straight off the
     * `KSAnnotation` instead. Use this for any single-`KClass` annotation member rather than a `supplier`
     * that dereferences it through the proxy.
     */
    fun <A : Annotation?> getAnnotationClassMember(
        annotation: A,
        annotationType: KClass<out Annotation>,
        member: String,
        supplier: A.() -> KClass<*>,
    ): TypeDefinition?

}

class RuntimeTypeFactory : TypeFactory {

    override fun <A : Annotation?> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition =
        supplier.invoke(annotation).toTypeDefinition()

    override fun <A : Annotation?, R : Annotation> getTypeAnnotation(annotation: A, annotationType: KClass<R>, supplier: A.() -> KClass<*>): R? =
        supplier.invoke(annotation).findAnnotations(annotationType).firstOrNull()

    override fun <A : Annotation?> getEnumValues(annotation: A, supplier: A.() -> KClass<*>): List<String>? =
        supplier.invoke(annotation)
            .takeIf { it.java.isEnum }
            ?.java
            ?.enumConstants
            ?.filterIsInstance<Enum<*>>()
            ?.map { it.name }

    override fun <A : Annotation?> getAnnotationClassMember(
        annotation: A,
        annotationType: KClass<out Annotation>,
        member: String,
        supplier: A.() -> KClass<*>,
    ): TypeDefinition? {
        val markerAnnotation = supplier.invoke(annotation).annotations.firstOrNull { annotationType.isInstance(it) } ?: return null
        // A `KClass` annotation member reflects as a java.lang.Class at runtime, so normalize before mapping.
        val value = when (val raw = annotationType.members.first { it.name == member }.call(markerAnnotation)) {
            is KClass<*> -> raw
            is Class<*> -> raw.kotlin
            else -> return null
        }
        return value.toTypeDefinition().takeIf { it.qualifiedName != NULL_CLASS::class.qualifiedName }
    }

}