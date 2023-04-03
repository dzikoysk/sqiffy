package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.toClassName
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
            .build()

        tableClass.writeTo(context.codeGenerator, Dependencies(true))
    }

    private fun generateColumnInitializer(property: PropertyData): String {
        var baseColumn = with(property) {
            when (property.type) {
                DataType.SERIAL -> "integer(${q(name)})"
                DataType.CHAR -> "char(${q(name)})"
                DataType.VARCHAR -> "varchar(${q(name)}, ${property.details})"
                DataType.BINARY -> "binary(${q(name)})"
                DataType.UUID_TYPE -> "uuid(${q(name)})"
                DataType.TEXT -> "text(${q(name)})"
                DataType.BOOLEAN -> "bool(${q(name)})"
                DataType.INT -> "integer(${q(name)})"
                DataType.LONG -> "long(${q(name)})"
                DataType.FLOAT -> "float(${q(name)})"
                DataType.DOUBLE -> "double(${q(name)})"
                DataType.DATE -> "date(${q(name)})"
                DataType.DATETIME -> "datetime(${q(name)})"
                DataType.TIMESTAMP -> "timestamp(${q(name)})"
                DataType.ENUM -> "enumeration(${q(name)}, ${property.enumDefinition?.enumData?.mappedTo}::class)"
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