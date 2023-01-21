package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.NULL_STRING
import com.dzikoysk.sqiffy.PropertyDefinitionType.ADD
import com.dzikoysk.sqiffy.PropertyDefinitionType.REMOVE
import com.dzikoysk.sqiffy.PropertyDefinitionType.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionType.RETYPE
import com.dzikoysk.sqiffy.shared.replaceFirst
import com.dzikoysk.sqiffy.toPropertyData

class ChangeLogPropertiesGenerator {

    internal fun generateProperties(context: ChangeLogGeneratorContext) {
        with(context) {
            when {
                changeToApply.name != NULL_STRING && state.tableName != changeToApply.name -> {
                    // rename table
                    registerChange(sqlGenerator.renameTable(state.tableName, changeToApply.name))
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
            if (changeToApply.properties.any { it.definitionType != ADD }) {
                throw IllegalStateException("You can only add properties to a new table scheme")
            }

            changeToApply.properties
                .map { it.toPropertyData() }
                .let { propertyDataList ->
                    registerChange(sqlGenerator.createTable(state.tableName, propertyDataList))
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
                val property = propertyChange.toPropertyData()

                when (propertyChange.definitionType) {
                    ADD -> {
                        registerChange(sqlGenerator.createColumn(state.tableName, property))
                        properties.add(property)
                    }
                    RENAME -> {
                        registerChange(sqlGenerator.renameColumn(state.tableName, propertyChange.name, propertyChange.rename))
                        properties.replaceFirst({ it.name == propertyChange.name }, { it.copy(name = propertyChange.rename) })
                    }
                    RETYPE -> {
                        // SQLite may not support this
                    }
                    REMOVE -> {
                        registerChange(sqlGenerator.removeColumn(state.tableName, property.name))
                        properties.removeIf { it.name == property.name }
                    }
                }
            }
        }
    }

}