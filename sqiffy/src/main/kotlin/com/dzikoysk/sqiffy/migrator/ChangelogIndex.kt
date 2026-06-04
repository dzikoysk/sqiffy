package com.dzikoysk.sqiffy.migrator

import java.security.MessageDigest

/**
 * Reads a changelog index file: a minimal, Sqiffy-native manifest that lists migration scripts
 * in the exact order they must be applied. One path per line; blank lines and lines starting with
 * `#` are ignored. Entries are resolved relative to the directory the index file lives in, so an
 * index at `database/changelog.index` containing `1.0.0/001-init.sql` resolves to
 * `database/1.0.0/001-init.sql`. An entry starting with `/` is treated as absolute from the
 * classpath root.
 *
 * The index is deliberately explicit (no directory scanning): reads are O(index size), order is
 * deterministic, and it works inside shaded JARs where classpath directory listing is unreliable.
 */
object ChangelogIndex {

    fun read(indexPath: String, loader: ResourceLoader): List<String> {
        val content = loader.readText(indexPath)
            ?: throw MigrationException("Changelog index not found on classpath: $indexPath")

        val baseDir = indexPath.removePrefix("/").substringBeforeLast('/', missingDelimiterValue = "")

        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(baseDir, it) }
            .toList()
    }

    private fun resolve(baseDir: String, entry: String): String =
        when {
            entry.startsWith("/") -> entry.removePrefix("/")
            baseDir.isEmpty() -> entry
            else -> "$baseDir/$entry"
        }

}

/** Normalizes line endings so checksums are stable regardless of the platform that wrote the file. */
internal fun normalizeLineEndings(text: String): String =
    text.replace("\r\n", "\n").replace("\r", "\n")

internal fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
