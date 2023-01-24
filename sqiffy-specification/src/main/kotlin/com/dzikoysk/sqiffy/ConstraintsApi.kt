package com.dzikoysk.sqiffy

import kotlin.reflect.KClass

enum class ConstraintDefinitionType {
    ADD,
    REMOVE
}

enum class ConstraintType {
    PRIMARY_KEY,
    FOREIGN_KEY
}

@Target()
annotation class Constraint(
    val definitionType: ConstraintDefinitionType = ConstraintDefinitionType.ADD,
    val type: ConstraintType,
    val name: String,
    val on: String = NULL_STRING,
    val referenced: KClass<*> = NULL_CLASS::class,
    val references: String = NULL_STRING
)

sealed interface ConstraintData {
    val type: ConstraintType
    val name: String
    val on: String
}

data class PrimaryKey(
    override val name: String,
    override val on: String
) : ConstraintData {
    override val type: ConstraintType = ConstraintType.PRIMARY_KEY
}

data class ForeignKey(
    override val name: String,
    override val on: String,
    val referenced: TypeDefinition,
    val references: String
) : ConstraintData {
    override val type: ConstraintType = ConstraintType.FOREIGN_KEY
}
