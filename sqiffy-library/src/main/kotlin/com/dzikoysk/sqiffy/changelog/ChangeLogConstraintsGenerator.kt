package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.Constraint
import com.dzikoysk.sqiffy.ConstraintDefinitionType.ADD
import com.dzikoysk.sqiffy.ConstraintDefinitionType.REMOVE
import com.dzikoysk.sqiffy.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.ForeignKey
import com.dzikoysk.sqiffy.NULL_CLASS
import com.dzikoysk.sqiffy.NULL_STRING
import com.dzikoysk.sqiffy.PrimaryKey

internal class ChangeLogConstraintsGenerator {

    fun generateConstraints(context: ChangeLogGeneratorContext) {
        with(context) {
            for (constraint in changeToApply.constraints) {
                when (constraint.type) {
                    PRIMARY_KEY -> generatePrimaryKey(context, constraint)
                    FOREIGN_KEY -> generateForeignKey(context, constraint)
                }
            }
        }
    }

    private fun generatePrimaryKey(context: ChangeLogGeneratorContext, constraint: Constraint) =
        with (context) {
            when (constraint.definitionType) {
                ADD -> {
                    val primaryKey = PrimaryKey(
                        name = constraint.name,
                        on = constraint.on
                    )

                    val property = state.properties.first { it.name == primaryKey.on }
                    require(!property.nullable) { "Column marked as primary key is nullable" }
                    require(state.constraints.none { it.type == PRIMARY_KEY }) { "Table ${state.tableName} already has primary key" }

                    registerChange {
                        createPrimaryKey(
                            tableName = state.tableName,
                            name = primaryKey.name,
                            on = primaryKey.on
                        )
                    }

                    state.constraints.add(primaryKey)
                }
                REMOVE -> {
                    require(state.constraints.any { it.type == PRIMARY_KEY }) { "Table ${state.tableName} doesn't have primary key to remove" }
                    registerChange { removePrimaryKey(state.tableName, constraint.name) }
                }
            }
        }

    private fun generateForeignKey(context: ChangeLogGeneratorContext, constraint: Constraint) =
        with (context) {
            when (constraint.definitionType) {
                ADD -> {
                    val foreignKey = ForeignKey(
                        name = constraint.name,
                        on = constraint.on
                            .takeIf { it != NULL_STRING }
                            ?: throw IllegalStateException("Foreign key declaration misses `on` property"),
                        referenced = typeFactory.getTypeDefinition(constraint) { referenced }
                            .takeIf { it.qualifiedName != NULL_CLASS::class.qualifiedName }
                            ?: throw IllegalStateException("Foreign key declaration misses `referenced` class"),
                        references = constraint.references
                            .takeUnless { it == NULL_STRING }
                            ?: throw IllegalStateException("Foreign key declaration misses `references` property")
                    )

                    val foreignTable = currentScheme
                        .firstOrNull { it.source == foreignKey.referenced.qualifiedName }
                        ?: throw IllegalStateException("Foreign table ${foreignKey.referenced} not found")

                    val foreignColumn = foreignTable
                        .properties
                        .firstOrNull { it.name == constraint.references }
                        ?: throw IllegalStateException("Foreign table ${foreignKey.referenced} does not have column ${constraint.references}")

                    registerChange {
                        createForeignKey(
                            tableName = state.tableName,
                            name = foreignKey.name,
                            on = foreignKey.on,
                            foreignTable = foreignTable.tableName,
                            foreignColumn = foreignColumn.name
                        )
                    }

                    state.constraints.add(foreignKey)
                }
                REMOVE -> {
                    val removed = state.constraints.removeIf { it.name == constraint.name }
                    require(removed) { "Cannot remove foreign key, constraint ${constraint.name} not found" }

                    registerChange {
                        removeForeignKey(
                            tableName = state.tableName,
                            name = constraint.name
                        )
                    }
                }
            }
        }

}