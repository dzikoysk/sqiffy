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
    val value: ConstraintType,
    val on: String,
    val referenced: KClass<*> = NULL_CLASS::class,
    val references: String = NULL_STRING
)

sealed interface ConstraintData {
    val type: ConstraintType
    val on: String
}

data class PrimaryKey(
    override val on: String
) : ConstraintData {
    override val type: ConstraintType = ConstraintType.PRIMARY_KEY
}

data class ForeignKey(
    override val on: String,
    val referenced: KClass<*>,
    val references: String
) : ConstraintData {
    override val type: ConstraintType = ConstraintType.FOREIGN_KEY
}
