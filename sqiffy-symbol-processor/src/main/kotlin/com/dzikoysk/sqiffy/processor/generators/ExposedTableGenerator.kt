package com.dzikoysk.sqiffy.processor.generators

/*
class ExposedTableGenerator(private val context: KspContext) {

    internal fun generateTableClass(definitionEntry: DefinitionEntry, properties: List<PropertyData>) {
        val tableClass = FileSpec.builder(definitionEntry.packageName, definitionEntry.name +  "Table")
            .addImport("org.jetbrains.exposed.sql.javatime", "date", "datetime", "timestamp")
            .addType(
                TypeSpec.objectBuilder(definitionEntry.name + "Table")
                .superclass(Table::class)
                .addSuperclassConstructorParameter(CodeBlock.of(q(definitionEntry.definition.value.first().name)))
                .also { typeBuilder ->
                    properties.forEach {
                        val propertyType = Column::class
                            .asTypeName()
                            .parameterizedBy(
                                it.type!!
                                    .javaType
                                    .asTypeName()
                                    .copy(nullable = it.nullable)
                            )

                        val property = PropertySpec.builder(it.name, propertyType)
                            .initializer(CodeBlock.builder().add(generateColumnInitializer(it)).build())
                            .build()

                        typeBuilder.addProperty(property)
                    }
                }
                .build()
            )
            .build()

        tableClass.writeTo(context.codeGenerator, Dependencies(true))
    }

    private fun generateColumnInitializer(property: PropertyData): String {
        var baseColumn = with(property) {
            when (property.type) {
                CHAR -> "char(${q(name)})"
                VARCHAR -> "varchar(${q(name)}, ${property.details})"
                BINARY -> "binary(${q(name)})"
                UUID_BINARY -> "uuid(${q(name)})"
                UUID_VARCHAR -> "varchar(${q(name)}, 36)"
                TEXT -> "text(${q(name)})"
                BLOB -> "blob(${q(name)})"
                BOOLEAN -> "bool(${q(name)})"
                INT -> "integer(${q(name)})"
                FLOAT -> "float(${q(name)})"
                DOUBLE -> "double(${q(name)})"
                DATE -> "date(${q(name)})"
                DATETIME -> "datetime(${q(name)})"
                TIMESTAMP -> "timestamp(${q(name)})"
                else -> throw UnsupportedOperationException("Unsupported property type used as column ($property)")
            }
        }

        if (property.autoIncrement) {
            // baseColumn += ".autoIncrement()" Exposed sucks and quotes our already quoted column name
        }

        if (property.nullable) {
            baseColumn += ".nullable()"
        }

        return baseColumn
    }

    private fun q(text: String): String =
        '"' + "\\\"" + text + "\\\"" + '"'

}

 */