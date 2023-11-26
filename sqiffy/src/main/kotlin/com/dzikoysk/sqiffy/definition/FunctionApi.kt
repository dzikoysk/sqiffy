package com.dzikoysk.sqiffy.definition

import kotlin.annotation.AnnotationTarget.PROPERTY

@Target(PROPERTY)
annotation class FunctionDefinition(
    val name: String,
    val versions: Array<FunctionVersion>
)

data class FunctionDefinitionData(
    val name: String,
    val versions: List<FunctionVersionData>
)

@Target()
annotation class FunctionVersion(
    val version: String,
    val parameters: Array<String> = [],
    val returnType: String = NULL_STRING,
    val body: String,
)

data class FunctionVersionData(
    val version: String,
    val parameters: List<String>,
    val returnType: String,
    val content: String
)

fun FunctionDefinition.toFunctionData(): FunctionDefinitionData =
    FunctionDefinitionData(
        name = name,
        versions = versions.map {
            FunctionVersionData(
                version = it.version,
                parameters = it.parameters.toList(),
                returnType = it.returnType,
                content = it.body.trimIndent()
            )
        }
    )