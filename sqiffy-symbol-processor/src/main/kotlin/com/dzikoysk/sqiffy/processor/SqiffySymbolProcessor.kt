package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.Dialect
import com.dzikoysk.sqiffy.changelog.Changelog
import com.dzikoysk.sqiffy.changelog.ChangelogBuilder
import com.dzikoysk.sqiffy.changelog.generator.dialects.getSchemeGenerator
import com.dzikoysk.sqiffy.definition.ChangelogDefinition
import com.dzikoysk.sqiffy.definition.ChangelogProvider.LIQUIBASE
import com.dzikoysk.sqiffy.definition.ChangelogProvider.SQIFFY
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DtoDefinition
import com.dzikoysk.sqiffy.definition.DtoGroupData
import com.dzikoysk.sqiffy.definition.FunctionDefinition
import com.dzikoysk.sqiffy.definition.ParsedDefinition
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.REMOVE
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.definition.TypeDefinition
import com.dzikoysk.sqiffy.definition.TypeFactory
import com.dzikoysk.sqiffy.definition.toDtoDefinitionData
import com.dzikoysk.sqiffy.definition.toFunctionData
import com.dzikoysk.sqiffy.definition.toPropertyData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.generators.DslTableGenerator
import com.dzikoysk.sqiffy.processor.generators.DtoGenerator
import com.dzikoysk.sqiffy.processor.generators.EntityGenerator
import com.dzikoysk.sqiffy.processor.generators.EnumGenerator
import com.dzikoysk.sqiffy.processor.generators.FunctionGenerator
import com.dzikoysk.sqiffy.processor.generators.LiquibaseGenerator
import com.dzikoysk.sqiffy.processor.generators.TableNamesGenerator
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
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import java.util.LinkedList

class SqiffySymbolProcessorProvider : SymbolProcessorProvider {

    data class KspContext(
        val logger: KSPLogger,
        val codeGenerator: CodeGenerator
    )

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        if (environment.options["debug"] == "true") {
            println("Sqiffy: Debug mode enabled")
            Thread.sleep(10000) // wait for debugger xD
        }

        return SqiffySymbolProcessor(
            KspContext(
                logger = environment.logger,
                codeGenerator = environment.codeGenerator
            )
        )
    }

}

internal class SqiffySymbolProcessor(private val context: KspContext) : SymbolProcessor {

    private val enumGenerator = EnumGenerator(context)
    private val functionGenerator = FunctionGenerator(context)
    private val dtoGenerator = DtoGenerator(context)
    private val entityGenerator = EntityGenerator(context)
    private val tableNamesGenerator = TableNamesGenerator(context)
    private val dslTableGenerator = DslTableGenerator(context)

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val typeFactory = KspTypeFactory(resolver)

        val changelogDefinition = resolver.getSymbolsWithAnnotation(ChangelogDefinition::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .flatMap { it.getAnnotationsByType(ChangelogDefinition::class) }
            .toList()
            .also { require(it.size <= 1) { "Only one ChangelogDefinition is allowed per project" } }
            .firstOrNull()

        val schemaGenerator = (changelogDefinition?.dialect ?: Dialect.POSTGRESQL)
            .getSchemeGenerator()

        val functions = resolver.getSymbolsWithAnnotation(FunctionDefinition::class.qualifiedName!!)
            .filterIsInstance<KSPropertyDeclaration>()
            .flatMap { it.getAnnotationsByType(FunctionDefinition::class) }
            .map { it.toFunctionData() }
            .toList()

        val tableDefinitions = resolver.getSymbolsWithAnnotation(Definition::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        val tables = tableDefinitions
            .flatMap { it.getAnnotationsByType(Definition::class).map { annotation -> it to annotation } }
            .map { (clazz, annotation) ->
                ParsedDefinition(
                    source = clazz.qualifiedName!!.asString(),
                    packageName = clazz.packageName.asString(),
                    name = clazz.simpleName.asString().substringBeforeLast("Definition"),
                    definition = annotation
                )
            }
            .toList()

        val baseSchemeGenerator = ChangelogBuilder(
            sqlSchemeGenerator = schemaGenerator,
            typeFactory = typeFactory
        )

        val changelog = baseSchemeGenerator.generateChangeLog(
            functions = functions,
            tables = tables
        )

        if (changelogDefinition != null) {
            when (changelogDefinition.provider) {
                SQIFFY -> { /* currently only supported at runtime */ }
                LIQUIBASE -> LiquibaseGenerator(context).generateLiquibaseChangeLog(
                    projectName = changelogDefinition.projectName,
                    changeLog = changelog
                )
            }
        }

        if (tables.isNotEmpty()) {
            val dtoDefinitions = resolver.getSymbolsWithAnnotation(DtoDefinition::class.qualifiedName!!)
                .filterIsInstance<KSClassDeclaration>()
                .toList()

            when {
                resolver.getAllFiles().none { it.fileName == "${tables.first().name}Table.kt" } -> {
                    generateTableDls(
                        typeFactory = typeFactory,
                        changeLog = changelog,
                    )
                    return dtoDefinitions + tableDefinitions
                }
                else -> {
                    generateEntityDsl(
                        typeFactory = typeFactory,
                        changeLog = changelog,
                        dtoGroups = dtoDefinitions
                            .filter { it.validate() }
                            .flatMap { it.getAnnotationsByType(DtoDefinition::class) }
                            .map { it.toDtoDefinitionData(typeFactory) }
                    )
                }
            }
        }

        return emptyList()
    }

    private fun generateTableDls(typeFactory: TypeFactory, changeLog: Changelog) {
        changeLog.enums.forEach { (reference, state) ->
            enumGenerator.generateEnum(reference, state)
        }

        changeLog.tables.forEach { (definition, name) ->
            val properties = generateProperties(
                typeFactory = typeFactory,
                table = definition
            )

            dslTableGenerator.generateTableClass(
                parsedDefinition = definition,
                properties = properties
            )

            tableNamesGenerator.generateTableNamesClass(
                parsedDefinition = definition,
                tableName = name,
                properties = properties
            )
        }
    }

    private fun generateEntityDsl(typeFactory: TypeFactory, changeLog: Changelog, dtoGroups: List<DtoGroupData>) {
        changeLog.tables.forEach { (definition, _) ->
            val properties = generateProperties(typeFactory, definition)

            val dtoMethodsToAdd = dtoGroups.firstOrNull { it.from.qualifiedName == definition.source }
                ?.variants
                ?.map { variant ->
                    dtoGenerator.generateDtoClass(
                        definition = definition,
                        variantData = variant,
                        selectedProperties = properties.filter { variant.allProperties || it.name in variant.properties }
                    )
                }
                ?: emptyList()

            entityGenerator.generateEntityClass(
                parsedDefinition = definition,
                properties = properties,
                dtoMethods = dtoMethodsToAdd
            )
        }
    }

    private fun generateProperties(typeFactory: TypeFactory, table: ParsedDefinition): LinkedList<PropertyData> {
        val properties = LinkedList<PropertyData>()

        for (definitionVersion in table.definition.versions) {
            for (property in definitionVersion.properties) {
                val convertedProperty = property.toPropertyData(typeFactory)

                when (property.operation) {
                    ADD -> properties.add(convertedProperty)
                    RENAME -> require(properties.replaceFirst({ it.name == property.name }, { it.copy(name = property.rename) }))
                    RETYPE -> require(properties.replaceFirst({ it.name == property.name }, { it.copy(type = property.type, details = property.details) }))
                    REMOVE -> properties.removeIf { it.name == property.name }
                }
            }
        }

        return properties
    }

}

fun TypeDefinition.toClassName(): ClassName =
    ClassName.bestGuess(qualifiedName)