package com.dzikoysk.sqiffy.changelog.generator.dialects

import com.dzikoysk.sqiffy.Dialect
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator

fun Dialect.getSchemeGenerator(): SqlSchemeGenerator = when (this) {
    Dialect.MYSQL -> MySqlSchemeGenerator
    Dialect.POSTGRESQL -> PostgreSqlSchemeGenerator
    Dialect.SQLITE -> SqliteSchemeGenerator
}