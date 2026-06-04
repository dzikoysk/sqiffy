package com.dzikoysk.sqiffy.migrator

import java.security.MessageDigest

/** Reads a classpath resource as UTF-8 text, or `null` if it does not exist. */
fun classpathResource(path: String): String? =
    FileMigrator::class.java.classLoader
        .getResourceAsStream(path.removePrefix("/"))
        ?.use { it.readBytes().toString(Charsets.UTF_8) }

/** Reads the changelog index: migration script paths, one per line (`#` comments allowed), applied in order. */
object ChangelogIndex {

    fun read(indexPath: String, loader: (path: String) -> String?): List<String> {
        val content = loader(indexPath)
            ?: throw MigrationException("Changelog index not found on classpath: $indexPath")

        val baseDir = indexPath.removePrefix("/").substringBeforeLast('/', missingDelimiterValue = "")

        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(baseDir, it) }
            .distinct() // a path listed twice is the same migration; keep it once
            .toList()
    }

    private fun resolve(baseDir: String, entry: String): String =
        when {
            entry.startsWith("/") -> entry.removePrefix("/")
            baseDir.isEmpty() -> entry
            else -> "$baseDir/$entry"
        }

}

// normalized so checksums don't depend on the platform that wrote the file
internal fun normalizeLineEndings(text: String): String =
    text.replace("\r\n", "\n").replace("\r", "\n")

internal fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
