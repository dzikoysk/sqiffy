package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DefinitionEntry
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

    internal fun generateTableNamesClass(definitionEntry: DefinitionEntry, tableName: String, properties: List<PropertyData>) {
        val infrastructurePackage = definitionEntry.getInfrastructurePackage()

        val namesClass = FileSpec.builder(infrastructurePackage, definitionEntry.name +  "TableNames")
            .addType(
                TypeSpec.objectBuilder(definitionEntry.name + "TableNames")
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