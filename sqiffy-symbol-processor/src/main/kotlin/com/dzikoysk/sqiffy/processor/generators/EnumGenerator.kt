package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.definition.EnumReference
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class EnumGenerator(private val context: KspContext) {

    internal fun generateEnum(enumReference: EnumReference, enumState: EnumState): String {
        val enumQualifier = enumReference.getEnumClassQualifier()

        val enumClass = FileSpec.builder(enumQualifier.packageName!!, enumQualifier.simpleName)
            .addType(
                TypeSpec.enumBuilder(enumQualifier.simpleName)
                    .also { typeBuilder ->
                        typeBuilder.addType(
                            TypeSpec.companionObjectBuilder()
                                .addProperty(
                                    PropertySpec.builder("TYPE_NAME", String::class)
                                        .initializer("%S", enumReference.enumData.name)
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
        return enumQualifier.qualifiedName
    }

}