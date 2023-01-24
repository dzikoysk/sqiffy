package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.Definition
import com.dzikoysk.sqiffy.DefinitionEntry
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.PropertyDefinitionOperation.ADD
import com.dzikoysk.sqiffy.PropertyDefinitionOperation.REMOVE
import com.dzikoysk.sqiffy.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.TypeDefinition
import com.dzikoysk.sqiffy.TypeFactory
import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.generators.EntityGenerator
import com.dzikoysk.sqiffy.processor.generators.ExposedTableGenerator
import com.dzikoysk.sqiffy.shared.replaceFirst
import com.google.devtools.ksp.KSTypeNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.util.LinkedList
import kotlin.reflect.KClass

class SqiffySymbolProcessorProvider : SymbolProcessorProvider {

    data class KspContext(
        val logger: KSPLogger,
        val codeGenerator: CodeGenerator
    )

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        Thread.sleep(10000) // wait for debugger xD

        return SqiffySymbolProcessor(
            KspContext(
                logger = environment.logger,
                codeGenerator = environment.codeGenerator
            )
        )
    }

}

internal class SqiffySymbolProcessor(context: KspContext) : SymbolProcessor {

    private val entityGenerator = EntityGenerator(context)
    private val exposedTableGenerator = ExposedTableGenerator(context)

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val tables = resolver.getSymbolsWithAnnotation(Definition::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .map {
                DefinitionEntry(
                    source = it.qualifiedName!!.asString(),
                    packageName = it.packageName.asString(),
                    name = it.simpleName.asString().substringBeforeLast("Definition"),
                    definition = it.getAnnotationsByType(Definition::class).first(),
                )
            }
            .toList()

        if (tables.isNotEmpty()) {
            val baseSchemeGenerator = ChangeLogGenerator(KspTypeFactory())
            baseSchemeGenerator.generateChangeLog(tables)
            generateDls(tables)
        }

        return emptyList()
    }

    private fun generateDls(tables: List<DefinitionEntry>) {
        tables.forEach { table ->
            val properties = LinkedList<PropertyData>()

            for (definitionVersion in table.definition.value) {
                for (property in definitionVersion.properties) {
                    val convertedProperty = PropertyData(
                        name = property.name,
                        type = property.type,
                        details = property.details,
                        nullable = property.nullable,
                        autoIncrement = property.autoincrement
                    )

                    when (property.operation) {
                        ADD -> properties.add(convertedProperty)
                        RENAME -> require(properties.replaceFirst({ it.name == property.name }, { it.copy(name = property.rename) }))
                        RETYPE -> require(properties.replaceFirst({ it.name == property.name }, { it.copy(type = property.type, details = property.details) }))
                        REMOVE -> properties.removeIf { it.name == property.name }
                    }
                }
            }

            entityGenerator.generateEntityClass(table, properties)
            exposedTableGenerator.generateTableClass(table, properties)
        }
    }

}

private class KspTypeFactory : TypeFactory {

    @OptIn(KspExperimental::class)
    override fun <A : Annotation> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition =
        try {
            supplier(annotation)
            throw IllegalStateException("Property accessor should throw KSTypeNotPresentException")
        } catch (exception: KSTypeNotPresentException) {
            exception.ksType.let {
                TypeDefinition(
                    packageName = it.declaration.packageName.asString(),
                    qualifiedName = it.declaration.qualifiedName!!.asString(),
                    simpleName = it.declaration.simpleName.asString()
                )
            }
        }

}
