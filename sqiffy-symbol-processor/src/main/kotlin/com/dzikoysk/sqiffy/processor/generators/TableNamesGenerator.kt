package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.ParsedDefinition
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.CONST
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class TableNamesGenerator(private val context: KspContext) {

    internal fun generateTableNamesClass(parsedDefinition: ParsedDefinition, tableName: String, properties: List<PropertyData>) {
        val infrastructurePackage = parsedDefinition.getInfrastructurePackage()

        val namesClass = FileSpec.builder(infrastructurePackage, parsedDefinition.name +  "TableNames")
            .addType(
                TypeSpec.objectBuilder(parsedDefinition.name + "TableNames")
                    .also { typeBuilder ->
                        typeBuilder.addProperty(
                            PropertySpec.builder("TABLE", String::class.asTypeName())
                                .initializer("%S", tableName)
                                .build()
                        )

                        properties.forEach { property ->
                            typeBuilder.addProperty(
                                PropertySpec.builder(property.name.uppercase(), String::class.asTypeName(), CONST)
                                    .initializer("%S", property.name)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .build()

        namesClass.writeTo(context.codeGenerator, Dependencies(true))
    }

}