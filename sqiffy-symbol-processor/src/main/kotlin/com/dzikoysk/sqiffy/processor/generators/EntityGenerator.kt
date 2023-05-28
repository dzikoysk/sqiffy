package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.dzikoysk.sqiffy.processor.toClassName
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class EntityGenerator(private val context: KspContext) {

    internal fun generateEntityClass(definitionEntry: DefinitionEntry, properties: List<PropertyData>, dtoMethods: List<Pair<FileSpec, List<PropertyData>>>) {
        val entityClass = generateEntityClass(
            packageName = definitionEntry.packageName,
            name = definitionEntry.name,
            properties = properties,
            dtoMethods = dtoMethods
        ).build()

        val entityClassName = ClassName(entityClass.packageName, entityClass.name)
        entityClass.writeTo(context.codeGenerator, Dependencies(true))

        if (properties.any { it.type == DataType.SERIAL }) {
            val serialProperties = properties.filter { it.type == DataType.SERIAL }

            val requiredProperties = properties
                .filter { it.type != DataType.SERIAL }
                .takeIf { it.isNotEmpty() }
                ?: return

            val unidentifiedEntityBuilder = generateEntityClass(
                packageName = definitionEntry.packageName,
                name = "Unidentified" + definitionEntry.name,
                properties = requiredProperties,
                dtoMethods = dtoMethods,
                extra = { typeSpec ->
                    typeSpec.addFunction(
                        FunSpec.builder("withId")
                            .also { functionBuilder ->
                                serialProperties.forEach {
                                    functionBuilder.addParameter(it.name, it.type!!.contextualType(it).toClassName())
                                }
                            }
                            .returns(entityClassName)
                            .addStatement(
                                "return %T(%L, %L)",
                                entityClassName,
                                serialProperties.joinToString(", ") { it.name + " = " + it.name },
                                requiredProperties.joinToString(", ") { it.name + " = " + it.name }
                            )
                            .build()
                    )
                }
            )

            return unidentifiedEntityBuilder.build().writeTo(context.codeGenerator, Dependencies(true))
        }
    }

    private fun generateEntityClass(
        packageName: String,
        name: String,
        properties: List<PropertyData>,
        dtoMethods: List<Pair<FileSpec, List<PropertyData>>>,
        extra: (TypeSpec.Builder) -> Unit = {}
    ): FileSpec.Builder =
        FileSpec.builder(packageName, name)
            .addType(
                TypeSpec.classBuilder(name)
                    .addModifiers(DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .also { constructorBuilder ->
                                properties.forEach {
                                    constructorBuilder.addParameter(it.name, it.type!!.contextualType(it).toClassName().copy(nullable = it.nullable))
                                }
                            }
                            .build()
                    )
                    .also { typeBuilder ->
                        properties.forEach {
                            typeBuilder.addProperty(
                                PropertySpec.builder(it.name, it.type!!.contextualType(it).toClassName().copy(nullable = it.nullable))
                                    .initializer(it.name)
                                    .build()
                            )
                        }
                    }
                    .also {
                        dtoMethods.forEach { (dtoClass, selectedProperties) ->
                            val dtoClassName = ClassName(dtoClass.packageName, dtoClass.name)

                            it.addFunction(
                                FunSpec.builder("to${dtoClass.name}")
                                    .returns(dtoClassName)
                                    .addStatement(
                                        "return %T(%L)",
                                        dtoClassName,
                                        selectedProperties.joinToString(", ") { it.name }
                                    )
                                    .build()
                            )
                        }
                    }
                    .also { extra(it) }
                    .build()
            )

}