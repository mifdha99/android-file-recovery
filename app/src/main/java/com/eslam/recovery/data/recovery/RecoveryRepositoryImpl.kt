package com.eslam.recovery.data.recovery

import android.content.Context
import android.media.MediaScannerConnection
import android.os.StatFs
import com.eslam.recovery.data.DispatcherProvider
import com.eslam.recovery.domain.model.FileTypes
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.RecoveryFailure
import com.eslam.recovery.domain.model.RecoveryProgress
import com.eslam.recovery.domain.model.RecoverySummary
import com.eslam.recovery.domain.model.RecoveryUpdate
import com.eslam.recovery.domain.repository.RecoveryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Copies candidates into RecoveredFiles/. Sources are only ever read, never moved, renamed or deleted. */
class RecoveryRepositoryImpl(
    context: Context,
    private val dispatchers: DispatcherProvider,
) : RecoveryRepository {
    private val appContext = context.applicationContext

    override fun recover(items: List<RecoverableFile>): Flow<RecoveryUpdate> = channelFlow {
        val root = RecoveryDestination.root()
        val totalBytes = items.sumOf { it.size }
        preflightError(root, totalBytes)?.let { error ->
            send(RecoveryUpdate.Finished(RecoverySummary(emptySet(), emptyList(), root.path, error)))
            return@channelFlow
        }

        val permits = Semaphore(PARALLEL_COPIES)
        val nameLock = Mutex()
        val reserved = HashSet<String>()
        val doneFiles = AtomicInteger()
        val doneBytes = AtomicLong()
        fun report() {
            trySend(RecoveryUpdate.Progress(RecoveryProgress(doneFiles.get(), items.size, doneBytes.get(), totalBytes)))
        }
        report()

        val outcomes = items.map { item ->
            async {
                permits.withPermit {
                    val target = nameLock.withLock { uniqueTarget(root, item, reserved) }
                    copyTo(item, target) { doneBytes.addAndGet(it); report() }
                        .also { doneFiles.incrementAndGet(); report() }
                }
            }
        }.awaitAll()

        val recovered = outcomes.filterIsInstance<Outcome.Ok>()
        if (recovered.isNotEmpty()) {
            // Makes recovered photos and videos show up in the gallery straight away.
            MediaScannerConnection.scanFile(appContext, recovered.map { it.target.path }.toTypedArray(), null, null)
        }
        send(
            RecoveryUpdate.Finished(
                RecoverySummary(
                    recoveredIds = recovered.mapTo(HashSet()) { it.id },
                    failures = outcomes.filterIsInstance<Outcome.Failed>().map { it.failure },
                    destination = root.path,
                )
            )
        )
    }
        .buffer(Channel.CONFLATED)
        .flowOn(dispatchers.io)

    private fun preflightError(root: File, totalBytes: Long): String? {
        root.mkdirs()
        if (!root.isDirectory) return "Cannot create ${root.path}"
        val free = runCatching { StatFs(root.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        return if (free < totalBytes + totalBytes / 20) "Not enough free space. Select fewer files." else null
    }

    /** Writes to `<target>.part` and renames at the end, so a cancelled or failed copy never leaves a broken file. */
    private suspend fun copyTo(item: RecoverableFile, target: File, onBytes: (Long) -> Unit): Outcome {
        val part = File(target.path + ".part")
        return try {
            target.parentFile?.mkdirs()
            copyRange(item, part, onBytes)
            check(part.renameTo(target)) { "Cannot finish file" }
            if (item.lastModified > 0) target.setLastModified(item.lastModified)
            Outcome.Ok(item.id, target)
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            Outcome.Failed(RecoveryFailure(item.displayName, e.message ?: e.javaClass.simpleName))
        }
    }

    private suspend fun copyRange(item: RecoverableFile, dest: File, onBytes: (Long) -> Unit) {
        val context = currentCoroutineContext()
        FileInputStream(item.path).channel.use { input ->
            FileOutputStream(dest).channel.use { output ->
                var pos = item.carve?.offset ?: 0L
                val end = item.carve?.let { it.offset + it.length } ?: input.size()
                while (pos < end) {
                    context.ensureActive()
                    // transferTo may move fewer bytes than asked, so loop until the range is done.
                    val n = input.transferTo(pos, minOf(CHUNK, end - pos), output)
                    if (n <= 0) break
                    pos += n
                    onBytes(n)
                }
                check(pos == end) { "Incomplete copy" }
            }
        }
    }

    /** Original name back, detected extension appended when missing, " (1)" suffix on clashes. */
    private fun uniqueTarget(root: File, item: RecoverableFile, reserved: MutableSet<String>): File {
        val dir = File(root, RecoveryDestination.folderFor(item.type))
        var name = item.displayName.replace('/', '_').trimStart('.').ifEmpty { "file_${item.id}" }
        if (FileTypes.fromName(name) == null && item.kind.extension.isNotEmpty()) name += "." + item.kind.extension
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = File(dir, name)
        var n = 1
        while (!reserved.add(candidate.path) || candidate.exists()) candidate = File(dir, "$base (${n++})$ext")
        return candidate
    }

    private sealed interface Outcome {
        data class Ok(val id: Long, val target: File) : Outcome
        data class Failed(val failure: RecoveryFailure) : Outcome
    }

    private companion object {
        const val PARALLEL_COPIES = 3
        const val CHUNK = 8L shl 20
    }
}
