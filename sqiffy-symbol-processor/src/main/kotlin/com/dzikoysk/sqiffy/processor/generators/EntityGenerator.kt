package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.NULL_VALUE
import com.dzikoysk.sqiffy.definition.ParsedDefinition
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.dsl.Row
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

    internal fun generateEntityClass(parsedDefinition: ParsedDefinition, properties: List<PropertyData>, dtoMethods: List<Pair<FileSpec, List<PropertyData>>>) {
        val domainPackage = parsedDefinition.getDomainPackage()
        val entityName = parsedDefinition.name

        val entityFile =
            generateEntityClass(
                packageName = domainPackage,
                name = entityName,
                properties = properties,
                dtoMethods = dtoMethods
            )
            .addFunction(
                FunSpec.builder("to$entityName")
                    .receiver(Row::class)
                    .returns(ClassName(parsedDefinition.getDomainPackage(), parsedDefinition.name))
                    .addStatement(
                        "return %T(\n%L\n)",
                        ClassName(domainPackage, entityName),
                        properties.joinToString(",\n") {
                            "    ${it.formattedName} = this[${parsedDefinition.getInfrastructurePackage()}.${entityName}Table.${it.formattedName}]"
                        }
                    )
                    .build()
            )
            .build()

        val entityClassName = ClassName(entityFile.packageName, entityName)
        entityFile.writeTo(context.codeGenerator, Dependencies(true))

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
                name = "Unidentified${parsedDefinition.name}",
                properties = requiredProperties,
                dtoMethods = matchedDtoMethods,
                extra = { typeSpec ->
                    typeSpec.addFunction(
                        FunSpec.builder("withId")
                            .also { functionBuilder ->
                                serialProperties.forEach {
                                    functionBuilder.addParameter(it.formattedName, it.type!!.contextualType(it).toClassName())
                                }
                            }
                            .returns(entityClassName)
                            .addStatement(
                                "return %T(%L, %L)",
                                entityClassName,
                                serialProperties.joinToString(", ") { it.formattedName + " = " + it.formattedName },
                                requiredProperties.joinToString(", ") { it.formattedName + " = " + it.formattedName }
                            )
                            .build()
                    )
                }
            )

            unidentifiedEntityBuilder.build().writeTo(context.codeGenerator, Dependencies(true))
        }
    }

    private fun generateEntityClass(
        packageName: String,
        name: String,
        properties: List<PropertyData>,
        dtoMethods: List<Pair<FileSpec, List<PropertyData>>>,
        extra: (TypeSpec.Builder) -> Unit = {}
    ): FileSpec.Builder =
        FileSpec.builder(packageName, "${name}Entity")
            .addType(
                TypeSpec.classBuilder(name)
                    .addModifiers(DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .also { constructorBuilder ->
                                properties.forEach {
                                    val dataType = it.type!!
                                    val typeName = dataType.toTypeName(it).copy()
                                    var builder = ParameterSpec.builder(it.formattedName, typeName)

                                    if (it.default != null && !it.rawDefault) {
                                        builder = builder.defaultValue(it.default!!.toKotlinCode(dataType, typeName))
                                    }

                                    constructorBuilder.addParameter(builder.build())
                                }
                            }
                            .build()
                    )
                    .also { typeBuilder ->
                        properties.forEach {
                            typeBuilder.addProperty(
                                PropertySpec.builder(it.formattedName, it.type!!.toTypeName(it))
                                    .initializer(it.formattedName)
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
                                        selectedProperties.joinToString(", ") { "${it.formattedName} = ${it.formattedName}" }
                                    )
                                    .build()
                            )
                        }
                    }
                    .also { extra(it) }
                    .build()
            )

    private fun String.toKotlinCode(dataType: DataType, typeName: TypeName): String {
        if (this == NULL_VALUE) {
            return "null"
        }

        return when (dataType) {
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
            DataType.NUMERIC -> "BigDecimal(\"${this}\")"
            DataType.DECIMAL -> "BigDecimal(\"${this}\")"
            DataType.DATE -> "LocalDate.parse(\"$this\")"
            DataType.DATETIME -> "LocalDateTime.parse(\"$this\")"
            DataType.TIMESTAMP -> "Instant.parse(\"$this\")"
            else -> this
        }
    }
}