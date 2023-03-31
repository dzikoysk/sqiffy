package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class DslTableGenerator(private val context: KspContext) {

    internal fun generateTableClass(definitionEntry: DefinitionEntry, properties: List<PropertyData>) {
        val tableClass = FileSpec.builder(definitionEntry.packageName, definitionEntry.name +  "Table")
            // .addImport("org.jetbrains.exposed.sql.javatime", "date", "datetime", "timestamp")
            .addType(
                TypeSpec.objectBuilder(definitionEntry.name + "Table")
                    .superclass(Table::class)
                    .addSuperclassConstructorParameter(CodeBlock.of(q(definitionEntry.definition.value.first().name)))
                    .also { typeBuilder ->
                        properties.forEach {
                            val propertyType = Column::class
                                .asTypeName()
                                .parameterizedBy(
                                    it.type!!
                                        .javaType
                                        .asTypeName()
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
            .build()

        tableClass.writeTo(context.codeGenerator, Dependencies(true))
    }

    private fun generateColumnInitializer(property: PropertyData): String {
        var baseColumn = with(property) {
            when (property.type) {
                com.dzikoysk.sqiffy.definition.DataType.SERIAL -> "integer(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.CHAR -> "char(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.VARCHAR -> "varchar(${q(name)}, ${property.details})"
                com.dzikoysk.sqiffy.definition.DataType.BINARY -> "binary(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE -> "uuid(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.TEXT -> "text(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.BOOLEAN -> "bool(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.INT -> "integer(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.LONG -> "long(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.FLOAT -> "float(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.DOUBLE -> "double(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.DATE -> "date(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.DATETIME -> "datetime(${q(name)})"
                com.dzikoysk.sqiffy.definition.DataType.TIMESTAMP -> "timestamp(${q(name)})"
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