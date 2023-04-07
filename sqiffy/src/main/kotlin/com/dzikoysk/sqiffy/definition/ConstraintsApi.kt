package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.ADD_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.definition.ConstraintType.PRIMARY_KEY
import kotlin.reflect.KClass

enum class ConstraintDefinitionType {
    ADD_CONSTRAINT,
    REMOVE_CONSTRAINT
}

enum class ConstraintType {
    PRIMARY_KEY,
    FOREIGN_KEY
}

@Target()
annotation class Constraint(
    val definitionType: ConstraintDefinitionType = ADD_CONSTRAINT,
    val type: ConstraintType,
    val name: String,
    val on: Array<String> = [],
    val referenced: KClass<*> = NULL_CLASS::class,
    val references: String = NULL_STRING
)

sealed interface ConstraintData {
    val type: ConstraintType
    val name: String
}

data class PrimaryKey(
    override val name: String,
    val on: List<String>
) : ConstraintData {
    override val type: ConstraintType = PRIMARY_KEY
}

data class ForeignKey(
    override val name: String,
    val on: String,
    val referenced: TypeDefinition,
    val references: String
) : ConstraintData {
    override val type: ConstraintType = FOREIGN_KEY
}
