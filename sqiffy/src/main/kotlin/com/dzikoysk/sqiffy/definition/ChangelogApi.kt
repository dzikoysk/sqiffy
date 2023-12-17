package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.dialect.Dialect
import kotlin.annotation.AnnotationRetention.RUNTIME

enum class ChangelogProvider {
    SQIFFY,
    LIQUIBASE
}

@Retention(RUNTIME)
annotation class ChangelogDefinition(
    val dialect: Dialect,
    val provider: ChangelogProvider = ChangelogProvider.SQIFFY,
    val projectName: String = "sqiffy",
)