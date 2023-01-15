package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.DataType.BINARY
import com.dzikoysk.sqiffy.DataType.BLOB
import com.dzikoysk.sqiffy.DataType.BOOLEAN
import com.dzikoysk.sqiffy.DataType.CHAR
import com.dzikoysk.sqiffy.DataType.DATE
import com.dzikoysk.sqiffy.DataType.DATETIME
import com.dzikoysk.sqiffy.DataType.DOUBLE
import com.dzikoysk.sqiffy.DataType.FLOAT
import com.dzikoysk.sqiffy.DataType.INT
import com.dzikoysk.sqiffy.DataType.TEXT
import com.dzikoysk.sqiffy.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.DataType.UUID_VARCHAR
import com.dzikoysk.sqiffy.DataType.VARCHAR
import com.dzikoysk.sqiffy.DefinitionEntry
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

class ExposedTableGenerator(private val context: KspContext) {

    internal fun generateTableClass(definitionEntry: DefinitionEntry, properties: List<PropertyData>) {
        val tableClass = FileSpec.builder(definitionEntry.packageName, definitionEntry.name +  "Table")
            .addImport("org.jetbrains.exposed.sql.javatime", "date", "datetime", "timestamp")
            .addType(
                TypeSpec.objectBuilder(definitionEntry.name + "Table")
                .superclass(Table::class)
                .addSuperclassConstructorParameter(CodeBlock.of(q(definitionEntry.definition.value.first().name)))
                .also { typebuilder ->
                    properties.forEach {
                        typebuilder.addProperty(
                            PropertySpec.builder(it.name, Column::class.asTypeName().parameterizedBy(it.type!!.javaType.asTypeName()))
                                .initializer(CodeBlock.builder().add(generateColumnInitializer(it)).build())
                                .build()
                        )
                    }
                }
                .build()
            )
            .build()

        tableClass.writeTo(context.codeGenerator, Dependencies(true))
    }

    private fun generateColumnInitializer(property: PropertyData): String =
        with (property) {
            when (property.type) {
                CHAR -> "char(${q(name)})"
                VARCHAR -> "varchar(${q(name)}, ${property.details})"
                BINARY -> "binary(${q(name)})"
                UUID_VARCHAR -> "uuid(${q(name)})"
                TEXT -> "text(${q(name)})"
                BLOB -> "blob(${q(name)})"
                BOOLEAN -> "bool(${q(name)})"
                INT -> "integer(${q(name)})"
                FLOAT -> "float(${q(name)})"
                DOUBLE -> "double(${q(name)})"
                DATE -> "date(${q(name)})"
                DATETIME -> "datetime(${q(name)})"
                TIMESTAMP -> "timestamp(${q(name)})"
                else -> throw UnsupportedOperationException("Unsupported property type used as column ($property)")
            }
        }

    private fun q(text: String): String =
        '"' + "\\\"" + text + "\\\"" + '"'

}