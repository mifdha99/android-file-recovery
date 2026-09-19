package com.eslam.recovery.domain.scan

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong

/**
 * Parallel directory walk. One coroutine per directory on a bounded IO dispatcher; the enclosing
 * scope completing is the termination condition, so an unreadable directory can never hang the scan.
 */
class Walker(
    private val parallelism: Int = Runtime.getRuntime().availableProcessors().coerceIn(4, 8),
    private val skipDir: (File) -> Boolean = { false },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val dirsScanned = AtomicLong()
    val filesScanned = AtomicLong()

    fun interface Visitor {
        /** Called concurrently from several threads. */
        fun onFile(file: File, size: Long, lastModified: Long, flags: Int)
    }

    suspend fun walk(roots: List<File>, visitor: Visitor) {
        withContext(dispatcher.limitedParallelism(parallelism)) {
            roots.forEach { root -> launch { visit(root, 0, 0, visitor) } }
        }
    }

    private fun CoroutineScope.visit(dir: File, flags: Int, depth: Int, visitor: Visitor) {
        ensureActive()
        val names = dir.list() ?: return // unreadable (e.g. Android/data on Android 11+)
        dirsScanned.incrementAndGet()
        val here = if (flags and DirFlags.HIDDEN == 0 && ".nomedia" in names) flags or DirFlags.HIDDEN else flags
        for (name in names) {
            val file = File(dir, name)
            // One stat per entry instead of isDirectory + length + lastModified.
            val attrs = try {
                Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (_: Exception) {
                continue
            }
            if (attrs.isDirectory) {
                if (depth < MAX_DEPTH && !DirFlags.shouldSkip(name, dir.name) && !skipDir(file)) {
                    val child = DirFlags.childFlags(here, name, dir.name, depth)
                    launch { visit(file, child, depth + 1, visitor) }
                }
            } else if (attrs.isRegularFile) {
                filesScanned.incrementAndGet()
                if (attrs.size() > 0) visitor.onFile(file, attrs.size(), attrs.lastModifiedTime().toMillis(), here)
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 64
    }
}
