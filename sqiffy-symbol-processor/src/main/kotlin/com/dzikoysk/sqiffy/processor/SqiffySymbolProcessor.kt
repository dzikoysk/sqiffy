package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.Definition
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.PropertyDefinitionType.ADD
import com.dzikoysk.sqiffy.PropertyDefinitionType.REMOVE
import com.dzikoysk.sqiffy.PropertyDefinitionType.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionType.RETYPE
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.generators.EntityGenerator
import com.dzikoysk.sqiffy.processor.generators.ExposedTableGenerator
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

class SqiffySymbolProcessorProvider : SymbolProcessorProvider {

    data class KspContext(
        val logger: KSPLogger,
        val codeGenerator: CodeGenerator
    )

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        Thread.sleep(4000) // wait for debugger

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
            .associateBy { it.simpleName.asString().substringBeforeLast("Definition") }

        tables.forEach { (name, type) ->
            val definition = type.getAnnotationsByType(Definition::class).first()
            val properties = LinkedList<PropertyData>()

            for (definitionVersion in definition.value) {
                for (property in definitionVersion.properties) {
                    val convertedProperty = PropertyData(
                        name = property.name,
                        type = property.type,
                        details = property.details,
                        nullable = property.nullable,
                        autoIncrement = property.autoincrement
                    )

                    when (property.definitionType) {
                        ADD -> properties.add(convertedProperty)
                        RENAME -> require(properties.replaceFirst({ it.name == property.name }, { it.copy(name = property.rename) }))
                        RETYPE -> require(properties.replaceFirst({ it.name == property.name }, { it.copy(type = property.retypeType, details = property.retypeDetails) }))
                        REMOVE -> properties.removeIf { it.name == property.name }
                    }
                }
            }

            entityGenerator.generateEntityClass(name, type, properties)
            exposedTableGenerator.generateTableClass(name, type, properties)
        }

        return emptyList()
    }

    private fun <T> LinkedList<T>.replaceFirst(condition: (T) -> Boolean, value: (T) -> T): Boolean =
        indexOfFirst(condition)
            .takeIf { it != -1}
            ?.also { this[it] = value(this[it]) } != null
            //?: run { add(value(null)) }

}