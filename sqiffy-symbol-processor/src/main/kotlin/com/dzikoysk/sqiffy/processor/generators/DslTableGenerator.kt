package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.ParsedDefinition
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.DslHandle
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.TableWithAutogeneratedKey
import com.dzikoysk.sqiffy.dsl.statements.AutogeneratedKeyInsertStatement
import com.dzikoysk.sqiffy.dsl.statements.InsertStatement
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.toClassName
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class DslTableGenerator(private val context: KspContext) {

    internal fun generateTableClass(parsedDefinition: ParsedDefinition, properties: List<PropertyData>) {
        val infrastructurePackage = parsedDefinition.getInfrastructurePackage()
        val autogeneratedKey = properties.firstOrNull { it.type == DataType.SERIAL }
        val objectName = parsedDefinition.name + "Table"

        val tableClass = FileSpec.builder(infrastructurePackage, parsedDefinition.name +  "Table")
            //.addImport("org.jetbrains.exposed.sql.javatime", "date", "datetime", "timestamp")
            .addType(
                TypeSpec.objectBuilder(objectName)
                    .superclass(
                        when {
                            autogeneratedKey != null -> TableWithAutogeneratedKey::class
                            else -> Table::class
                        }
                    )
                    .addSuperclassConstructorParameter(CodeBlock.of(q(parsedDefinition.definition.versions.first().name)))
                    .also {
                        if (autogeneratedKey != null) {
                            it.addFunction(
                                FunSpec.builder("getAutogeneratedKey")
                                    .addModifiers(KModifier.OVERRIDE)
                                    .addStatement("""return ${autogeneratedKey.name}""")
                                    .returns(Column::class.asTypeName().parameterizedBy(autogeneratedKey.type!!.toTypeName(autogeneratedKey)))
                                    .build()
                            )
                        }
                    }
                    .also { typeBuilder ->
                        properties.forEach {
                            val propertyType = Column::class
                                .asTypeName()
                                .parameterizedBy(
                                    it.type!!
                                        .contextualType(it)
                                        .toClassName()
                                        .copy(nullable = it.nullable)
                                )

                            val property = PropertySpec.builder(it.name, propertyType)
                                .initializer(CodeBlock.builder().add(generateColumnInitializer(it)).build())
                                .build()

                            typeBuilder.addProperty(property)
                        }
                    }
                    .build()
            )
            .also { typeBuilder ->
                generateInsertValues(typeBuilder, parsedDefinition, properties)
            }
            .also { typeBuilder ->
                typeBuilder.addFunction(FunSpec.builder("insert")
                    .receiver(DslHandle::class)
                    .addParameter(
                        ParameterSpec.builder("table", ClassName.bestGuess(objectName))
                            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                                .addMember("%S", "UNUSED_PARAMETER")
                                .build()
                            )
                            .build()
                    )
                    .addStatement("return ${objectName}InsertValues(this)")
                    .returns(ClassName(infrastructurePackage, "${objectName}InsertValues"))
                    .build()
                )
            }
            .build()

        tableClass.writeTo(context.codeGenerator, Dependencies(true))
    }

    private fun generateInsertValues(
        fileBuilder: FileSpec.Builder,
        parsedDefinition: ParsedDefinition,
        properties: List<PropertyData>,
        objectName: String = parsedDefinition.name + "Table"
    ) {
        val infrastructurePackage = parsedDefinition.getInfrastructurePackage()
        val domainPackage = parsedDefinition.getDomainPackage()

        val insertionProperties = properties.filter { it.type != DataType.SERIAL }
        val autogeneratedKey = properties.firstOrNull { it.type == DataType.SERIAL }

        val insertStatementReturnType = when {
            autogeneratedKey != null -> AutogeneratedKeyInsertStatement::class
            else -> InsertStatement::class
        }

        fileBuilder.addType(
            TypeSpec.classBuilder("${objectName}InsertValues")
                .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec
                        .builder("database", DslHandle::class)
                        .build())
                    .build()
                )
                .addProperty(PropertySpec
                    .builder("database", DslHandle::class)
                    .initializer("database")
                    .build()
                )
                .addFunction(
                    FunSpec.builder("values")
                        .also { valuesBuilder ->
                            insertionProperties.forEach { property ->
                                valuesBuilder.addParameter(ParameterSpec
                                    .builder(property.name, property.type!!.toTypeName(property))
                                    .also { if (property.nullable) it.defaultValue("null") }
                                    .build()
                                )
                            }
                        }
                        .addStatement(
                            """
                            |return database.insert(%T) {
                            |  %L
                            |}
                            """.trimMargin(),
                            ClassName(infrastructurePackage, objectName),
                            insertionProperties.joinToString("; ") { "it[${objectName}.${it.name}] = ${it.name}" }
                        )
                        .returns(insertStatementReturnType)
                        .build()
                )
                .also { insertTypeBuilder ->
                    if (insertionProperties.isEmpty() && autogeneratedKey != null) {
                        return@also
                    }

                    insertTypeBuilder.addFunction(
                        FunSpec.builder("values")
                            .addParameter(
                                "entity",
                                when {
                                    autogeneratedKey != null -> ClassName(domainPackage, "Unidentified${parsedDefinition.name}")
                                    else -> ClassName(domainPackage, parsedDefinition.name)
                                }
                            )
                            .addCode(
                                """
                                |return database.insert(%T) {
                                |  %L
                                |}
                                """.trimMargin(),
                                ClassName(infrastructurePackage, objectName),
                                insertionProperties.joinToString(separator = ";\n  ") { "it[${objectName}.${it.name}] = entity.${it.name}" }
                            )
                            .returns(insertStatementReturnType)
                            .build()
                    )
                }
                .build()
        )
    }

    private fun generateColumnInitializer(property: PropertyData): String {
        var baseColumn = with(property) {
            val defaultDbType = q(type!!.name)

            when (property.type) {
                DataType.SERIAL -> "serial(${q(name)}, $defaultDbType)"
                DataType.CHAR -> "char(${q(name)}, $defaultDbType)"
                DataType.VARCHAR -> "varchar(${q(name)}, $defaultDbType)"
                DataType.BINARY -> "binary(${q(name)}, $defaultDbType)"
                DataType.UUID_TYPE -> "uuid(${q(name)}, $defaultDbType)"
                DataType.TEXT -> "text(${q(name)}, $defaultDbType)"
                DataType.BOOLEAN -> "bool(${q(name)}, $defaultDbType)"
                DataType.INT -> "integer(${q(name)}, $defaultDbType)"
                DataType.LONG -> "long(${q(name)}, $defaultDbType)"
                DataType.FLOAT -> "float(${q(name)}, $defaultDbType)"
                DataType.DOUBLE -> "double(${q(name)}, $defaultDbType)"
                DataType.DATE -> "date(${q(name)}, $defaultDbType)"
                DataType.DATETIME -> "datetime(${q(name)}, $defaultDbType)"
                DataType.TIMESTAMP -> "timestamp(${q(name)}, $defaultDbType)"
                DataType.ENUM -> "enumeration(${q(name)}, ${q(property.enumDefinition!!.name)}, ${property.enumDefinition?.mappedTo}::class)"
                else -> throw UnsupportedOperationException("Unsupported property type used as column ($property)")
            }
        }

        if (property.nullable) {
            baseColumn += ".nullable()"
        }

        return baseColumn
    }

    private fun q(text: String): String =
        '"' + text + '"'

}