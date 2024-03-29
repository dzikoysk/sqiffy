package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.ChangelogBuilder.ChangeLogGeneratorContext
import com.dzikoysk.sqiffy.definition.Constraint
import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.ADD_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.REMOVE_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.definition.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.ForeignKey
import com.dzikoysk.sqiffy.definition.PrimaryKey
import com.dzikoysk.sqiffy.definition.toData

internal class ChangelogConstraintsBuilder {

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
                ADD_CONSTRAINT -> {
                    val primaryKey = constraint.toData(typeFactory) as PrimaryKey

                    val primaryKeyProperties = primaryKey.on.map {
                        state.properties
                            .firstOrNull { property -> property.name == it }
                            ?: throw IllegalStateException("Column $it marked as primary key not found in table ${state.tableName}")
                    }

                    require(primaryKeyProperties.none { it.nullable }) { "Column marked as primary key is nullable (${constraint.name} = ${constraint.on.contentToString()})" }
                    require(state.constraints.none { it.type == PRIMARY_KEY }) { "Table ${state.tableName} already has primary key" }
                    checkIfConstraintOrIndexNameAlreadyUsed(primaryKey.name)

                    registerChange {
                        "create-primary-key-${primaryKey.name}" to createPrimaryKey(
                            tableName = state.tableName,
                            name = primaryKey.name,
                            on = primaryKeyProperties
                        )
                    }

                    state.constraints.add(primaryKey)
                }
                REMOVE_CONSTRAINT -> {
                    val removed = state.constraints.removeIf { it.name == constraint.name && it.type == PRIMARY_KEY }
                    require(removed) { "Table ${state.tableName} doesn't have primary key to remove" }

                    registerChange {
                        "remove-primary-key-${constraint.name}" to removePrimaryKey(state.tableName, constraint.name)
                    }
                }
            }
        }

    private fun generateForeignKey(context: ChangeLogGeneratorContext, constraint: Constraint) =
        with (context) {
            when (constraint.definitionType) {
                ADD_CONSTRAINT -> {
                    val foreignKey = constraint.toData(typeFactory) as ForeignKey
                    checkIfConstraintOrIndexNameAlreadyUsed(foreignKey.name)

                    val onColumn = state.properties
                        .firstOrNull { it.name == foreignKey.on }
                        ?: throw IllegalStateException("Column ${foreignKey.on} marked as foreign key not found in table ${state.tableName}")

                    onColumn
                        .takeIf { it.type != DataType.SERIAL }
                        ?: throw IllegalStateException("Column ${foreignKey.on} marked as foreign key cannot be of type SERIAL")

                    val foreignTable = currentScheme
                        .firstOrNull { it.source == foreignKey.referenced.qualifiedName }
                        ?: throw IllegalStateException("Foreign table ${foreignKey.referenced} not found")

                    val foreignColumn = foreignTable
                        .properties
                        .firstOrNull { it.name == constraint.references }
                        ?: throw IllegalStateException("Foreign table ${foreignKey.referenced} does not have column ${constraint.references}")

                    registerChange {
                        "create-foreign-key-${foreignKey.name}" to createForeignKey(
                            tableName = state.tableName,
                            name = foreignKey.name,
                            on = onColumn,
                            foreignTable = foreignTable.tableName,
                            foreignColumn = foreignColumn
                        )
                    }

                    state.constraints.add(foreignKey)
                }
                REMOVE_CONSTRAINT -> {
                    val removed = state.constraints.removeIf { it.name == constraint.name }
                    require(removed) { "Cannot remove foreign key, constraint ${constraint.name} not found" }

                    registerChange {
                        "remove-foreign-key-${constraint.name}" to removeForeignKey(
                            tableName = state.tableName,
                            name = constraint.name
                        )
                    }
                }
            }
        }

}