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
            val defaultDbType = q(type!!.name)

            when (property.type) {
                DataType.SERIAL -> "integer(${q(name)}, $defaultDbType)"
                DataType.CHAR -> "char(${q(name)}, $defaultDbType)"
                DataType.VARCHAR -> "varchar(${q(name)}, $defaultDbType, $details)"
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
                DataType.ENUM -> "enumeration(${q(name)}, ${q(property.enumDefinition!!.enumData.name)}, ${property.enumDefinition?.enumData?.mappedTo}::class)"
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