package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.processor.toClassName
import com.squareup.kotlinpoet.TypeName

fun DataType.toTypeName(propertyData: PropertyData): TypeName =
    contextualType(propertyData)
        .toClassName()
        .copy(nullable = propertyData.nullable)