package com.dzikoysk.sqiffy.processor.generators

import com.dzikoysk.sqiffy.changelog.Changelog
import com.dzikoysk.sqiffy.processor.SqiffySymbolProcessorProvider.KspContext
import com.google.devtools.ksp.processing.Dependencies

class LiquibaseGenerator(private val context: KspContext) {

    fun generateLiquibaseChangeLog(
        projectName: String,
        changeLog: Changelog
    ) {
        createFile(
            path = "liquibase/changelog-master.xml",
            content =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                            ${changeLog.getAllChanges().joinToString(separator = " ") { """<includeAll path="liquibase/${it.version}" />""" }}
                </databaseChangeLog>
                """.trimIndent()
        )

        changeLog.getAllChanges().forEach { schemaChange ->
            var changeId = 0

            schemaChange.changes.forEach { change ->
                val currentId = ++changeId

                createFile(
                    path = "liquibase/${schemaChange.version}/${"%04d".format(currentId)}-${change.description}.sql",
                    content = """
                        --liquibase formatted sql
                        --validCheckSum: 1:ANY
                        --changeset $projectName:$currentId splitStatements:false endDelimiter:;
                        %s
                    """.trimIndent().format(change.query)
                )
            }
        }
    }

    private fun createFile(path: String, content: String) {
        context.codeGenerator
            .createNewFileByPath(
                dependencies = Dependencies(aggregating = true),
                path = path.substringBeforeLast("."),
                extensionName = path.substringAfterLast(".")
            )
            .use { output ->
                output.writer().use {
                    it.write(content)
                }
            }
    }

}