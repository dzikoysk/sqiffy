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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class EntityGenerator(private val context: KspContext) {

    internal fun generateEntityClass(definitionEntry: DefinitionEntry, properties: List<PropertyData>, dtoMethods: List<Pair<FileSpec, List<PropertyData>>>) {
        val domainPackage = definitionEntry.getDomainPackage()

        val entityClass = generateEntityClass(
            packageName = definitionEntry.getDomainPackage(),
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

            val matchedDtoMethods = dtoMethods
                .filter { requiredProperties.containsAll(it.second) }

            val unidentifiedEntityBuilder = generateEntityClass(
                packageName = domainPackage,
                name = "Unidentified" + definitionEntry.name,
                properties = requiredProperties,
                dtoMethods = matchedDtoMethods,
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
                                    val dataType = it.type!!
                                    val typeName = dataType.toTypeName(it)
                                    var builder = ParameterSpec.builder(it.name, typeName)
                                    it.default?.let { defaultValue ->
                                        builder = builder.defaultValue(defaultValue.toKotlinCode(dataType, typeName))
                                    }
                                    constructorBuilder.addParameter(builder.build())
                                }
                            }
                            .build()
                    )
                    .also { typeBuilder ->
                        properties.forEach {
                            typeBuilder.addProperty(
                                PropertySpec.builder(it.name, it.type!!.toTypeName(it))
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
                                        selectedProperties.joinToString(", ") { "${it.name} = ${it.name}" }
                                    )
                                    .build()
                            )
                        }
                    }
                    .also { extra(it) }
                    .build()
            )

    private fun String.toKotlinCode(dataType: DataType, typeName: TypeName): String =
        when (dataType) {
            /* Special types */
            DataType.UUID_TYPE -> "UUID.fromString(\"$this\")"
            DataType.ENUM -> "$typeName.$this"

            /* Regular types */
            DataType.CHAR -> "'$this'"
            DataType.VARCHAR, DataType.TEXT -> "\"$this\""
            DataType.BINARY -> "\"$this\".toByteArray()"
            DataType.BOOLEAN -> this.toBoolean().toString()
            DataType.LONG -> "${this}L"
            DataType.FLOAT -> "${this}F"
            DataType.DATE -> "LocalDate.parse(\"$this\")"
            DataType.DATETIME -> "LocalDateTime.parse(\"$this\")"
            DataType.TIMESTAMP -> "Instant.parse(\"$this\")"
            else -> this
        }

}