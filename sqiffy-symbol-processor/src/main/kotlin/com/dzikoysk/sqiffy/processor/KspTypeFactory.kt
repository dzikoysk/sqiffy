package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.definition.TypeDefinition
import com.dzikoysk.sqiffy.definition.TypeFactory
import com.google.devtools.ksp.KSTypeNotPresentException
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

}