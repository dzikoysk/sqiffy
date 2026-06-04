package com.dzikoysk.sqiffy.migrator

import java.io.InputStream

/**
 * Resolves changelog index files and migration scripts by path.
 *
 * The default implementation reads from the classpath, which is what most applications want
 * (migrations packaged under `src/main/resources`). It is abstracted as an interface so the
 * source can be swapped for tests or for reading migrations from the filesystem.
 */
fun interface ResourceLoader {

    /** Opens the resource at [path], or returns `null` if it does not exist. */
    fun open(path: String): InputStream?

    fun readText(path: String): String? =
        open(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

}

/**
 * Loads resources from the classpath relative to the root (leading slashes are ignored), e.g.
 * `database/changelog.index`.
 */
class ClasspathResourceLoader(
    private val classLoader: ClassLoader = ClasspathResourceLoader::class.java.classLoader
) : ResourceLoader {

    override fun open(path: String): InputStream? =
        classLoader.getResourceAsStream(path.removePrefix("/"))

}
