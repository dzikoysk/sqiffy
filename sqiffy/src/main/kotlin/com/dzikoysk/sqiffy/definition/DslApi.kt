package com.dzikoysk.sqiffy.definition

@Target(AnnotationTarget.CLASS)
annotation class DslDefinition(
    val namingStrategy: NamingStrategy,
)

enum class NamingStrategy {
    /** 1:1 **/
    RAW,
    SNAKE_CASE,
    CAMEL_CASE,
}

object NamingStrategyFormatter {

    private val splitCamelRegex = "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])".toRegex()
    private val splitSnakeCase = "_".toRegex()

    fun format(strategy: NamingStrategy, name: String): String =
        when (strategy) {
            NamingStrategy.RAW -> name
            NamingStrategy.SNAKE_CASE -> toSnakeCase(name)
            NamingStrategy.CAMEL_CASE -> toCamelCase(name)
        }

    private fun toSnakeCase(name: String): String =
        splitCamelRegex.split(name).joinToString("_") { it.lowercase() }

    private fun toCamelCase(name: String): String =
        splitSnakeCase
            .split(name)
            .joinToString("") { word -> word.replaceFirstChar { it.uppercase() } }
            .replaceFirstChar { it.lowercase() }

}