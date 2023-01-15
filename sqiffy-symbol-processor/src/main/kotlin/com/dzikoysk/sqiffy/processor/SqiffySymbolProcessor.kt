package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.Definition
import com.dzikoysk.sqiffy.DefinitionEntry
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.PropertyDefinitionType.ADD
import com.dzikoysk.sqiffy.PropertyDefinitionType.REMOVE
import com.dzikoysk.sqiffy.PropertyDefinitionType.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionType.RETYPE
import com.dzikoysk.sqiffy.generator.BaseSchemeGenerator
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.generators.EntityGenerator
import com.dzikoysk.sqiffy.processor.generators.ExposedTableGenerator
import com.dzikoysk.sqiffy.shared.replaceFirst
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
        Thread.sleep(4000) // wait for debugger xD

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
    private val baseSchemeGenerator = BaseSchemeGenerator()

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val tables = resolver.getSymbolsWithAnnotation(Definition::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .map {
                DefinitionEntry(
                    packageName = it.packageName.asString(),
                    name = it.simpleName.asString().substringBeforeLast("Definition"),
                    definition = it.getAnnotationsByType(Definition::class).first(),
                )
            }
            .toList()

        if (tables.isNotEmpty()) {
            // baseSchemeGenerator.generateChangeLog(tables)
            generateDls(tables)
        }

        return emptyList()
    }

    private fun generateDls(tables: List<DefinitionEntry>) {
        tables.forEach {
            val properties = LinkedList<PropertyData>()

            for (definitionVersion in it.definition.value) {
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

            entityGenerator.generateEntityClass(it, properties)
            exposedTableGenerator.generateTableClass(it, properties)
        }
    }

}