package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.ParsedDefinition
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.VariantData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.toClassName
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

internal class DtoGenerator(private val context: KspContext) {

    internal fun generateDtoClass(definition: ParsedDefinition, variantData: VariantData, selectedProperties: List<PropertyData>): Pair<FileSpec, List<PropertyData>> {
        val dtoClass = FileSpec.builder(definition.getApiPackage(), variantData.name)
            .addType(
                TypeSpec.classBuilder(variantData.name)
                    .addModifiers(KModifier.DATA)
                    .also { classBuilder ->
                        variantData.implements.forEach {
                            classBuilder.addSuperinterface(it.toClassName())
                        }
                    }
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .also { constructorBuilder ->
                                selectedProperties.forEach {
                                    constructorBuilder.addParameter(it.formattedName, it.type!!.contextualType(it).toClassName().copy(nullable = it.nullable))
                                }
                            }
                            .build()
                    )
                    .also { typeBuilder ->
                        selectedProperties.forEach {
                            typeBuilder.addProperty(
                                PropertySpec.builder(it.formattedName, it.type!!.contextualType(it).toClassName().copy(nullable = it.nullable))
                                    .initializer(it.formattedName)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .build()

        dtoClass.writeTo(context.codeGenerator, Dependencies(true))
        return dtoClass to selectedProperties
    }

}