package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class EntityGenerator(private val context: KspContext) {

    internal fun generateEntityClass(name: String, type: KSClassDeclaration, properties: List<PropertyData>) {
        val entityClass = FileSpec.builder(type.packageName.asString(), name)
            .addType(
                TypeSpec.classBuilder(name)
                    .addModifiers(DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                        .also { constructorBuilder ->
                            properties.forEach {
                                constructorBuilder.addParameter(it.name, it.type.javaType)
                            }
                        }
                        .build()
                    )
                    .also { typeBuilder ->
                        properties.forEach {
                            typeBuilder.addProperty(
                                PropertySpec.builder(it.name, it.type.javaType)
                                    .initializer(it.name)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .build()

        entityClass.writeTo(context.codeGenerator, Dependencies(true))
    }

}