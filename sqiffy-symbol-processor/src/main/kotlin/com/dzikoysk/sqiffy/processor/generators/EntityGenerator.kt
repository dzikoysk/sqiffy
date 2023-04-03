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

    internal fun generateEntityClass(definitionEntry: DefinitionEntry, properties: List<PropertyData>) {
        val entityClass = generateEntityClass(definitionEntry.packageName, definitionEntry.name, properties).build()
        val entityClassName = ClassName(entityClass.packageName, entityClass.name)
        entityClass.writeTo(context.codeGenerator, Dependencies(true))

        if (properties.any { it.type == DataType.SERIAL }) { // TODO: Replace with check for PK+Serial
            val serialProperties = properties.filter { it.type == DataType.SERIAL }
            val requiredProperties = properties.filter { it.type != DataType.SERIAL }

            val unidentifiedEntityBuilder = generateEntityClass(
                packageName = definitionEntry.packageName,
                name = "Unidentified" + definitionEntry.name,
                properties = requiredProperties,
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

    private fun generateEntityClass(packageName: String, name: String, properties: List<PropertyData>, extra: (TypeSpec.Builder) -> Unit = {}): FileSpec.Builder =
        FileSpec.builder(packageName, name)
            .addType(
                TypeSpec.classBuilder(name)
                    .addModifiers(DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .also { constructorBuilder ->
                                properties.forEach {
                                    println(it)
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
                    .also { extra(it) }
                    .build()
            )

}