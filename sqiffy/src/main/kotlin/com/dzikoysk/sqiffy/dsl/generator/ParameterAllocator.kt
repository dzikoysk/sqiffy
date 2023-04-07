package com.dzikoysk.sqiffy.dsl.generator

class ParameterAllocator {

    private val arguments = mutableListOf<Argument>()

    fun allocate(): Argument =
        "${arguments.size}".also { arguments.add(it) }

}