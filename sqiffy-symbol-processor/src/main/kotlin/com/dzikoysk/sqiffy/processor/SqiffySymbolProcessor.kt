package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.changelog.ChangeLog
import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator
import com.dzikoysk.sqiffy.changelog.generator.dialects.PostgreSqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.DtoDefinition
import com.dzikoysk.sqiffy.definition.DtoGroupData
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.REMOVE
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.definition.TypeDefinition
import com.dzikoysk.sqiffy.definition.TypeFactory
import com.dzikoysk.sqiffy.definition.toDtoDefinitionData
import com.dzikoysk.sqiffy.definition.toPropertyData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.generators.DslTableGenerator
import com.dzikoysk.sqiffy.processor.generators.DtoGenerator
import com.dzikoysk.sqiffy.processor.generators.EntityGenerator
import com.dzikoysk.sqiffy.processor.generators.EnumGenerator
import com.dzikoysk.sqiffy.processor.generators.TableNamesGenerator
import com.dzikoysk.sqiffy.shared.replaceFirst
import com.google.devtools.ksp.KSTypeNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import java.util.LinkedList
import kotlin.reflect.KClass

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
    private val dtoGenerator = DtoGenerator(context)
    private val entityGenerator = EntityGenerator(context)
    private val tableNamesGenerator = TableNamesGenerator(context)
    private val dslTableGenerator = DslTableGenerator(context)

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val typeFactory = KspTypeFactory(resolver)

        val tableDefinitions = resolver.getSymbolsWithAnnotation(Definition::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        context.logger.warn("ROUND tables: " + tableDefinitions.size)

        val tables = tableDefinitions
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
            val baseSchemeGenerator = ChangeLogGenerator(
                sqlSchemeGenerator = PostgreSqlSchemeGenerator, // dialect doesn't matter
                typeFactory = typeFactory
            )

            val dtoDefinitions = resolver.getSymbolsWithAnnotation(DtoDefinition::class.qualifiedName!!)
                .filterIsInstance<KSClassDeclaration>()
                .toList()

            if (resolver.getAllFiles().any { it.fileName == "${tables.first().name}Table" }) {
                generateTableDls(
                    typeFactory = typeFactory,
                    changeLog = baseSchemeGenerator.generateChangeLog(tables),
                )
                return dtoDefinitions + tableDefinitions
            } else {
                generateEntityDsl(
                    typeFactory = typeFactory,
                    changeLog = baseSchemeGenerator.generateChangeLog(tables),
                    dtoGroups = dtoDefinitions
                        .filter { it.validate() }
                        .flatMap { it.getAnnotationsByType(DtoDefinition::class) }
                        .map { it.toDtoDefinitionData(typeFactory) }
                )
            }
        }

        return emptyList()
    }

    private fun generateTableDls(typeFactory: TypeFactory, changeLog: ChangeLog) {
        changeLog.enums.forEach { (reference, state) ->
            enumGenerator.generateEnum(reference, state)
        }

        changeLog.tables.forEach { (table, name) ->
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

            dslTableGenerator.generateTableClass(
                definitionEntry = table,
                properties = properties
            )

            tableNamesGenerator.generateTableNamesClass(
                definitionEntry = table,
                tableName = name,
                properties = properties
            )
        }
    }

    private fun generateEntityDsl(typeFactory: TypeFactory, changeLog: ChangeLog, dtoGroups: List<DtoGroupData>) {
        changeLog.tables.forEach { (table, name) ->
            val properties = LinkedList<PropertyData>()

            for (definitionVersion in table.definition.value) {
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

            val dtoMethodsToAdd = dtoGroups.firstOrNull { it.from.qualifiedName == table.source }
                ?.variants
                ?.map { variant ->
                    dtoGenerator.generateDtoClass(
                        definitionEntry = table,
                        variantData = variant,
                        selectedProperties = properties.filter { variant.properties.contains(it.name) } //.filter { it.name in variant.properties }
                    )
                }
                ?: emptyList()

            entityGenerator.generateEntityClass(
                definitionEntry = table,
                properties = properties,
                dtoMethods = dtoMethodsToAdd
            )
        }
    }

}

private class KspTypeFactory(private val resolver: Resolver) : TypeFactory {

    @OptIn(KspExperimental::class)
    private fun <A : Annotation> getKSType(annotation: A, supplier: A.() -> KClass<*>): KSType =
        try {
            val type = supplier(annotation)
            resolver.getClassDeclarationByName(type.qualifiedName!!)!!.asStarProjectedType()
        } catch (exception: KSTypeNotPresentException) {
            exception.ksType
        }

    override fun <A : Annotation> getTypeDefinition(annotation: A, supplier: A.() -> KClass<*>): TypeDefinition =
        getKSType(annotation, supplier).let {
            TypeDefinition(
                packageName = it.declaration.packageName.asString(),
                simpleName = it.declaration.simpleName.asString()
            )
        }

    @OptIn(KspExperimental::class)
    override fun <A : Annotation, R : Annotation> getTypeAnnotation(annotation: A, annotationType: KClass<R>, supplier: A.() -> KClass<*>): R? =
        getKSType(annotation, supplier).let { type ->
            type
                .let { it.declaration as? KSClassDeclaration }
                ?.getAnnotationsByType(annotationType)
                ?.firstOrNull()
        }

    override fun <A : Annotation> getEnumValues(annotation: A, supplier: A.() -> KClass<*>): List<String>? =
        getKSType(annotation, supplier).let { type ->
            type
                .takeIf { it.declaration.modifiers.contains(Modifier.ENUM) }
                .let { it?.declaration as? KSClassDeclaration }
                ?.declarations
                ?.filterIsInstance<KSClassDeclaration>()
                ?.filter { it.classKind == ClassKind.ENUM_ENTRY }
                ?.map { it.simpleName.asString() }
                ?.toList()
        }

}

fun TypeDefinition.toClassName(): ClassName =
    ClassName.bestGuess(qualifiedName)