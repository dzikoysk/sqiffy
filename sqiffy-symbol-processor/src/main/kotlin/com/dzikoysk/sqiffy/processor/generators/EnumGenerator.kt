package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.definition.TypeDefinition
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class EnumGenerator(private val context: KspContext) {

    internal fun generateEnum(enumState: EnumState, mappedTo: TypeDefinition): String {
        val enumClass = FileSpec.builder(mappedTo.packageName!!, mappedTo.simpleName)
            .addType(
                TypeSpec.enumBuilder(mappedTo.simpleName)
                    .also { typeBuilder ->
                        typeBuilder.addType(
                            TypeSpec.companionObjectBuilder()
                                .addProperty(
                                    PropertySpec.builder("TYPE_NAME", String::class)
                                        .initializer("%S", enumState.name)
                                        .build()
                                )
                                .build()
                        )

                        enumState.values.forEach {
                            typeBuilder.addEnumConstant(it, TypeSpec.anonymousClassBuilder().build())
                        }
                    }
                    .build()
            )
            .build()

        enumClass.writeTo(context.codeGenerator, Dependencies(true))
        return mappedTo.qualifiedName
    }

}