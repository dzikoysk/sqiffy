package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.definition.NULL_CLASS
import com.dzikoysk.sqiffy.definition.TypeDefinition
import com.dzikoysk.sqiffy.definition.TypeFactory
import com.google.devtools.ksp.KSTypeNotPresentException
import com.google.devtools.ksp.KSTypesNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind.ENUM_ENTRY
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier.ENUM
import kotlin.reflect.KClass

class KspTypeFactory(private val resolver: Resolver) : TypeFactory {

    @OptIn(KspExperimental::class)
    private fun <A : Annotation?> getKSType(annotation: A, supplier: A.() -> KClass<*>): KSType =
        try {
            val type = supplier(annotation)
            resolver.getClassDeclarationByName(type.qualifiedName!!)!!.asStarProjectedType()
        } catch (exception: KSTypeNotPresentException) {
            exception.ksType
        }

    @OptIn(KspExperimental::class)
    override fun <A : Annotation?> getTypeDefinitions(annotation: A, supplier: A.() -> Array<KClass<*>>): List<TypeDefinition> =
        try {
            supplier(annotation).map { kclass -> getTypeDefinition(annotation) { kclass } }
        } catch (exception: KSTypesNotPresentException) {
            exception.ksTypes.map { TypeDefinition(it.declaration.packageName.asString(), it.declaration.simpleName.asString()) }
        }

    override fun <A : Annotation?> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition =
        getKSType(annotation, supplier).let {
            TypeDefinition(
                packageName = it.declaration.packageName.asString(),
                simpleName = it.declaration.simpleName.asString()
            )
        }

    @OptIn(KspExperimental::class)
    override fun <A : Annotation?, R : Annotation> getTypeAnnotation(annotation: A, annotationType: KClass<R>, supplier: A.() -> KClass<*>): R? =
        getKSType(annotation, supplier).let { type ->
            type
                .let { it.declaration as? KSClassDeclaration }
                ?.getAnnotationsByType(annotationType)
                ?.firstOrNull()
        }

    override fun <A : Annotation?> getEnumValues(annotation: A, supplier: A.() -> KClass<*>): List<String>? =
        getKSType(annotation, supplier).let { type ->
            type
                .takeIf { it.declaration.modifiers.contains(ENUM) }
                .let { it?.declaration as? KSClassDeclaration }
                ?.declarations
                ?.filterIsInstance<KSClassDeclaration>()
                ?.filter { it.classKind == ENUM_ENTRY }
                ?.map { it.simpleName.asString() }
                ?.toList()
        }

    override fun <A : Annotation?> getAnnotationClassMember(
        annotation: A,
        annotationType: KClass<out Annotation>,
        member: String,
        supplier: A.() -> KClass<*>,
    ): TypeDefinition? =
        (getKSType(annotation, supplier).declaration as? KSClassDeclaration)
            ?.annotationClassMember(annotationType.qualifiedName!!, member)

}

/**
 * Reads a single `KClass`-typed [member] of the annotation [annotationFqn] on this declaration straight off
 * the [KSAnnotation], returning its type (or null when absent / `NULL_CLASS`). This is the proxy-free path
 * around KSP's `getAnnotationsByType` NPE on single-`KClass` members (see [TypeFactory.getAnnotationClassMember]).
 */
internal fun KSClassDeclaration.annotationClassMember(annotationFqn: String, member: String): TypeDefinition? {
    val type = annotations
        .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn }
        ?.arguments
        ?.firstOrNull { it.name?.asString() == member }
        ?.value as? KSType
        ?: return null

    return TypeDefinition(
        packageName = type.declaration.packageName.asString(),
        simpleName = type.declaration.simpleName.asString(),
    ).takeIf { it.qualifiedName != NULL_CLASS::class.qualifiedName }
}