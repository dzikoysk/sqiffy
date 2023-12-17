package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.ConstraintData
import com.dzikoysk.sqiffy.definition.DefinitionVersionData
import com.dzikoysk.sqiffy.definition.IndexData
import com.dzikoysk.sqiffy.definition.PropertyData
import java.util.Deque

internal data class TableAnalysisState(
    val changesToApply: Deque<DefinitionVersionData>,
    val source: String,
    var tableName: String,
    val properties: MutableList<PropertyData> = mutableListOf(),
    val constraints: MutableList<ConstraintData> = mutableListOf(),
    val indices: MutableList<IndexData> = mutableListOf()
)