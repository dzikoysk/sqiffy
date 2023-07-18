package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator.ChangeLogGeneratorContext
import com.dzikoysk.sqiffy.definition.Constraint
import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.ADD_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.REMOVE_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.definition.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.ForeignKey
import com.dzikoysk.sqiffy.definition.NULL_CLASS
import com.dzikoysk.sqiffy.definition.NULL_STRING
import com.dzikoysk.sqiffy.definition.PrimaryKey

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
                    val primaryKey = PrimaryKey(
                        name = constraint.name,
                        on = constraint.on
                            .takeIf { it.isNotEmpty() }
                            ?.toList()
                            ?: throw IllegalStateException("Primary key '${constraint.name}' declaration misses `on` property")
                    )

                    val primaryKeyProperties = primaryKey.on.map {
                        state.properties
                            .firstOrNull { property -> property.name == it }
                            ?: throw IllegalStateException("Column $it marked as primary key not found in table ${state.tableName}")
                    }

                    require(primaryKeyProperties.none { it.nullable }) { "Column marked as primary key is nullable (${constraint.name} = ${constraint.on.contentToString()})" }
                    require(state.constraints.none { it.type == PRIMARY_KEY }) { "Table ${state.tableName} already has primary key" }
                    checkIfConstraintOrIndexNameAlreadyUsed(primaryKey.name)

                    registerChange {
                        createPrimaryKey(
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
                        removePrimaryKey(state.tableName, constraint.name)
                    }
                }
            }
        }

    private fun generateForeignKey(context: ChangeLogGeneratorContext, constraint: Constraint) =
        with (context) {
            when (constraint.definitionType) {
                ADD_CONSTRAINT -> {
                    val foreignKey = ForeignKey(
                        name = constraint.name,
                        on = constraint.on
                            .takeIf { it.size == 1 }
                            ?.first()
                            ?: throw IllegalStateException("Foreign key '${constraint.name}' declaration misses `on` property or contains more than one column"),
                        referenced = typeFactory.getTypeDefinition(constraint) { referenced }
                            .takeIf { it.qualifiedName != NULL_CLASS::class.qualifiedName }
                            ?: throw IllegalStateException("Foreign key '${constraint.name}' declaration misses `referenced` class"),
                        references = constraint.references
                            .takeUnless { it == NULL_STRING }
                            ?: throw IllegalStateException("Foreign key '${constraint.name}' declaration misses `references` property")
                    )

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
                        createForeignKey(
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
                        removeForeignKey(
                            tableName = state.tableName,
                            name = constraint.name
                        )
                    }
                }
            }
        }

}