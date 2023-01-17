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
                changeToApply.name != NULL_STRING && state.name != changeToApply.name -> {
                    // rename table
                    registerChange(sqlGenerator.renameTable(state.name, changeToApply.name))
                    state.name = changeToApply.name
                }
                currentScheme.none { it.name == state.name } -> {
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
                    registerChange(sqlGenerator.createTable(state.name, propertyDataList))
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
                        registerChange(sqlGenerator.createColumn(state.name, property))
                        properties.add(property)
                    }
                    RENAME -> {
                        registerChange(sqlGenerator.renameColumn(state.name, propertyChange.name, propertyChange.rename))
                        properties.replaceFirst({ it.name == propertyChange.name }, { it.copy(name = propertyChange.rename) })
                    }
                    RETYPE -> {
                        // SQLite may not support this
                    }
                    REMOVE -> {
                        registerChange(sqlGenerator.removeColumn(state.name, property.name))
                        properties.removeIf { it.name == property.name }
                    }
                }
            }
        }
    }

}