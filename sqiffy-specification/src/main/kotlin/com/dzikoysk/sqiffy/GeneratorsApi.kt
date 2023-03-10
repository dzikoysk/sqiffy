package com.dzikoysk.sqiffy

import kotlin.reflect.KClass

class DefinitionEntry(
    val source: String,
    val packageName: String,
    val name: String,
    val definition: Definition,
)

class TypeDefinition(
    val packageName: String,
    val qualifiedName: String,
    val simpleName: String
)

fun KClass<*>.toTypeDefinition(): TypeDefinition =
    TypeDefinition(
        packageName = java.packageName,
        qualifiedName = qualifiedName!!,
        simpleName = simpleName!!
    )

interface TypeFactory {

    fun <A : Annotation> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition

}

class RuntimeTypeFactory : TypeFactory {

    override fun <A : Annotation> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition =
        supplier.invoke(annotation).toTypeDefinition()

}