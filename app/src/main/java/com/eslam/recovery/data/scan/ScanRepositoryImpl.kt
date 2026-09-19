package com.eslam.recovery.data.scan

import android.content.Context
import com.eslam.recovery.data.DispatcherProvider
import com.eslam.recovery.data.recovery.RecoveryDestination
import com.eslam.recovery.domain.model.FileTypes
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.ScanProgress
import com.eslam.recovery.domain.model.ScanUpdate
import com.eslam.recovery.domain.model.Source
import com.eslam.recovery.domain.repository.ScanRepository
import com.eslam.recovery.domain.scan.Classifier
import com.eslam.recovery.domain.scan.JpegCarver
import com.eslam.recovery.domain.scan.Match
import com.eslam.recovery.domain.scan.Signature
import com.eslam.recovery.domain.scan.TrashedName
import com.eslam.recovery.domain.scan.Walker
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/** Walks every storage volume in parallel while MediaStore is queried, then resolves thumbnails and blobs. */
class ScanRepositoryImpl(
    context: Context,
    private val dispatchers: DispatcherProvider,
) : ScanRepository {
    private val appContext = context.applicationContext
    private val mediaStore = MediaStoreSource(appContext)

    override fun scan(): Flow<ScanUpdate> = channelFlow {
        val session = Session()
        val ids = async { mediaStore.currentIds() }
        val trashedRows = async { mediaStore.trashed() }

        val ownOutput = RecoveryDestination.root().path
        val walker = Walker(skipDir = { it.path == ownOutput }, dispatcher = dispatchers.io)
        fun progress(finishing: Boolean = false) =
            ScanProgress(walker.dirsScanned.get(), walker.filesScanned.get(), session.count.get(), session.bytes.get(), finishing)

        val ticker = launch {
            while (isActive) {
                send(ScanUpdate.Progress(progress()))
                delay(PROGRESS_INTERVAL_MS)
            }
        }
        walker.walk(StorageRoots.all(appContext)) { file, size, lastModified, flags ->
            val match = Classifier.classify(file.name, size, flags) { Signature.detect(file) } ?: return@walk
            val item = match.toFile(session.nextId(), file.path, size, lastModified)
            when {
                match.carveBlob -> session.blobs += file
                match.thumbId != null -> session.thumbs += item to match.thumbId
                else -> session.add(item)
            }
        }
        ticker.cancel()
        send(ScanUpdate.Progress(progress(finishing = true)))

        // Thumbnails named after a MediaStore id only count when that id is gone. Without a
        // trustworthy id set the category is skipped rather than reporting every thumbnail.
        ids.await()?.let { known -> session.thumbs.forEach { (item, id) -> if (id !in known) session.add(item) } }
        session.blobs.forEach { blob -> ensureActive(); session.carve(blob) }
        trashedRows.await().forEach { row -> session.addTrashedRow(row) }

        send(ScanUpdate.Finished(session.found.values.sortedByDescending { it.size }))
    }
        // Progress is only a snapshot, so a slow collector just skips stale ones; the last element
        // (Finished) is always delivered.
        .buffer(Channel.CONFLATED)
        .flowOn(dispatchers.io)

    /** Thread-safe state of one scan. Lives only as long as the flow collection, so nothing leaks between scans. */
    private class Session {
        /** Keyed by path (+ carve offset), which also dedupes volumes that overlap. */
        val found = ConcurrentHashMap<String, RecoverableFile>()
        val thumbs = ConcurrentLinkedQueue<Pair<RecoverableFile, Long>>()
        val blobs = ConcurrentLinkedQueue<File>()
        val count = AtomicLong()
        val bytes = AtomicLong()
        private val ids = AtomicLong()

        fun nextId(): Long = ids.incrementAndGet()

        fun add(file: RecoverableFile) {
            val key = file.carve?.let { "${file.path}#${it.offset}" } ?: file.path
            if (found.putIfAbsent(key, file) == null) {
                count.incrementAndGet()
                bytes.addAndGet(file.size)
            }
        }

        fun carve(blob: File) {
            runCatching {
                JpegCarver.carve(blob) { range ->
                    add(
                        RecoverableFile(
                            id = nextId(), path = blob.path, displayName = "thumbnail_${range.offset}.jpg",
                            size = range.length.toLong(), lastModified = blob.lastModified(),
                            kind = JPEG, source = Source.THUMBNAIL, carve = range,
                        )
                    )
                }
            }
        }

        /** Anything MediaStore lists as trashed that the walk did not reach. */
        fun addTrashedRow(row: MediaStoreSource.TrashedRow) {
            if (found.containsKey(row.path)) return
            val file = File(row.path)
            if (!file.isFile || file.length() <= 0) return
            val original = TrashedName.parse(file.name)?.originalName ?: file.name
            val kind = FileTypes.fromName(original) ?: Signature.detect(file) ?: FileTypes.UNKNOWN
            add(RecoverableFile(nextId(), row.path, original, file.length(), row.modifiedMillis, kind, Source.TRASH, row.expiresMillis))
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 200L
        val JPEG = FileTypes.fromName("a.jpg")!!

        fun Match.toFile(id: Long, path: String, size: Long, lastModified: Long) =
            RecoverableFile(id, path, displayName, size, lastModified, kind, source, expiresAt)
    }
}
