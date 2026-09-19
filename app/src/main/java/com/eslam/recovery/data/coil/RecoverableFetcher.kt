package com.eslam.recovery.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.eslam.recovery.domain.model.RecoverableFile
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.RandomAccessFile

/**
 * Loads a [RecoverableFile] with the mime type detected during the scan. Trashed and recycle-bin
 * files often have no usable extension, and Coil picks the video decoder / EXIF handling by mime.
 */
class RecoverableFetcher(private val file: RecoverableFile) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val carve = file.carve
        val source = if (carve != null) {
            val bytes = ByteArray(carve.length)
            RandomAccessFile(file.path, "r").use { it.seek(carve.offset); it.readFully(bytes) }
            ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM)
        } else {
            // Videos must stay file-backed: the frame decoder hands the path to MediaMetadataRetriever.
            ImageSource(file = file.path.toPath(), fileSystem = FileSystem.SYSTEM)
        }
        return SourceFetchResult(source = source, mimeType = file.mime, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<RecoverableFile> {
        override fun create(data: RecoverableFile, options: Options, imageLoader: ImageLoader): Fetcher =
            RecoverableFetcher(data)
    }
}

class RecoverableKeyer : Keyer<RecoverableFile> {
    override fun key(data: RecoverableFile, options: Options): String =
        "${data.path}|${data.carve?.offset}|${data.size}|${data.lastModified}"
}
