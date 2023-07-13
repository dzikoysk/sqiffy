package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator.ChangeLogGeneratorContext
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.NULL_TYPE
import com.dzikoysk.sqiffy.definition.NULL_STRING
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.REMOVE
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.definition.toPropertyData
import com.dzikoysk.sqiffy.shared.replaceFirst

class ChangelogPropertiesBuilder {

    internal fun generateProperties(context: ChangeLogGeneratorContext) {
        with(context) {
            when {
                /* rename table */
                changeToApply.name != NULL_STRING && state.tableName != changeToApply.name -> {
                    registerChange {
                        renameTable(state.tableName, changeToApply.name)
                    }
                    state.tableName = changeToApply.name
                }
                currentScheme.none { it.tableName == state.tableName } -> {
                    generateNewTable(context)
                    return // properties are up-to-date
                }
            }
            generateChangesForProperties(context)
        }
    }

    private fun generateNewTable(context: ChangeLogGeneratorContext) {
        with(context) {
            if (changeToApply.properties.any { it.operation != ADD }) {
                throw IllegalStateException("You can only add properties to a new table scheme")
            }

            changeToApply.properties
                .map { it.toPropertyData(context.typeFactory) }
                .onEach {
                    if (it.type == ENUM) {
                        val enumDefinition = it.enumDefinition ?: throw IllegalStateException("Enum definition cannot be null for ${it.name} in ${state.tableName}")
                        context.currentEnums.defineEnum(enumDefinition)
                    }
                }
                .let { propertyDataList ->
                    registerChange {
                        createTable(
                            name = state.tableName,
                            properties = propertyDataList,
                            enums = currentEnums
                        )
                    }
                    state.properties.addAll(propertyDataList)
                }

            currentScheme.add(state)
        }
    }

    private fun generateChangesForProperties(context: ChangeLogGeneratorContext) {
        with(context) {
            val properties = context.state.properties

            // detect properties change in existing table
            for (propertyChange in changeToApply.properties) {
                when (propertyChange.operation) {
                    ADD -> {
                        val property = propertyChange.toPropertyData(context.typeFactory)
                        require(properties.none { it.name == property.name }) { "Cannot add property ${property.name} to ${state.tableName} because it already exists" }

                        if (property.type == ENUM) {
                            val enumDefinition = property.enumDefinition ?: throw IllegalStateException("Enum definition cannot be null for ${property.name} in ${state.tableName}")
                            context.currentEnums.defineEnum(enumDefinition)
                        }

                        properties.add(property)

                        registerChange {
                            createColumn(
                                tableName = state.tableName,
                                property = property,
                                enums = currentEnums
                            )
                        }
                    }
                    RENAME -> {
                        val replaced = properties.replaceFirst(
                            condition = { it.name == propertyChange.name },
                            newValue = { currentProperty ->
                                currentProperty.copy(
                                    name = propertyChange.rename
                                        .takeIf { it != NULL_STRING }
                                        ?: throw IllegalStateException("Cannot rename property ${propertyChange.name} to null in ${state.tableName}")
                                )
                            }
                        )

                        require(replaced) { "Cannot rename property ${propertyChange.name} to ${propertyChange.rename} in ${state.tableName} because it does not exist" }

                        registerChange {
                            renameColumn(state.tableName, propertyChange.name, propertyChange.rename)
                        }
                    }
                    // <!> SQLite may not support this
                    RETYPE -> {
                        val currentProperty = state.properties
                            .firstOrNull { it.name == propertyChange.name }
                            ?: throw IllegalStateException("Cannot retype property ${propertyChange.name} in ${state.tableName} because it does not exist")

                        val newProperty = currentProperty.copy(
                            type = propertyChange.type.takeIf { it != NULL_TYPE },
                            details = propertyChange.details.takeIf { it != NULL_STRING },
                        )

                        if (newProperty.type == ENUM) {
                            throw IllegalStateException("Cannot retype property ${propertyChange.name} in ${state.tableName} to enum. Migrate to a new typ using a temporary column.")
                        }

                        properties.replaceFirst(
                            condition = { it.name == propertyChange.name },
                            newValue = { _ -> newProperty }
                        )

                        registerChange {
                            retypeColumn(
                                tableName = state.tableName,
                                oldProperty = currentProperty,
                                newProperty = newProperty,
                                enums = currentEnums
                            )
                        }
                    }
                    REMOVE -> {
                        val removed = properties.removeIf { it.name == propertyChange.name }
                        require(removed) { "Cannot remove property ${propertyChange.name} from ${state.tableName} because it does not exist" }

                        registerChange {
                            removeColumn(state.tableName, propertyChange.name)
                        }
                    }
                }
            }
        }
    }

}