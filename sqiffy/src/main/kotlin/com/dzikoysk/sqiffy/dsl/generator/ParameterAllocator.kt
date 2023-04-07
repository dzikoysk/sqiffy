package com.dzikoysk.sqiffy.dsl.generator

import com.dzikoysk.sqiffy.dsl.Values
import org.jdbi.v3.core.statement.SqlStatement

typealias Argument = String

class ParameterAllocator {

    private val arguments = mutableListOf<Argument>()

    fun allocate(): Argument =
        "${arguments.size}".also { arguments.add(it) }

}

enum class ArgumentType {
    VALUE,
    COLUMN
}

data class Bind(
    val type: ArgumentType,
    val value: Any
)

class Arguments(private val allocator: ParameterAllocator) {

    val arguments: MutableMap<Argument, Bind> = mutableMapOf()

    fun createArgument(type: ArgumentType, value: Any): Argument {
        val argument = allocator.allocate()
        arguments[argument] = Bind(type, value)
        return argument
    }

    operator fun plus(arguments: Arguments?): Arguments =
        Arguments(allocator).also {
            it.arguments.putAll(this.arguments);
            it.arguments.putAll(arguments?.arguments ?: emptyMap())
        }

    override fun toString(): String =
        arguments.toString()

}

fun <S : SqlStatement<*>> S.bindArguments(arguments: Arguments, values: Values? = null): S = also {
    arguments.arguments.forEach { (arg, bind) ->
        when (bind.type) {
            ArgumentType.VALUE -> bindByType(arg, bind.value, bind.value::class.javaObjectType)
            ArgumentType.COLUMN -> {
                if (values == null) {
                    throw IllegalStateException("Cannot bind columns without values")
                }
                val column = values.getColumn(bind.value.toString())!!
                val value = values.getValue(column)
                bindByType(arg, value, column.type)
            }
        }
    }
}