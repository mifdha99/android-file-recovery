package com.eslam.recovery.domain.scan

import com.eslam.recovery.domain.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class WalkerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(path: String, size: Int = 20_000, header: ByteArray? = null) {
        val f = File(tmp.root, path)
        f.parentFile!!.mkdirs()
        f.writeBytes(ByteArray(size).also { a -> header?.copyInto(a) })
    }

    @Test fun findsOnlyLostFiles() = runTest {
        val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        file("DCIM/Camera/IMG_1.jpg")                                   // visible: ignored
        file("DCIM/Camera/.trashed-1893456000-IMG_2.jpg")               // TRASH
        file("Android/.Trash/com.sec.android.gallery3d/abc123", header = jpegMagic) // BIN, sniffed
        file("Pictures/.thumbnails/77.jpg")                             // THUMBNAIL
        file("Music/.thumbnails/5.jpg")                                 // skipped dir
        file("WhatsApp/Media/.Statuses/s.mp4")                          // HIDDEN
        file("Private/.nomedia", size = 1); file("Private/p.png")       // HIDDEN via .nomedia
        file("Android/media/app/files/7ff873720154ffada211bf", header = jpegMagic) // CACHE
        file("Android/media/com.whatsapp/WhatsApp Images/IMG-WA1.jpg")  // visible: ignored
        file("Download/empty.jpg", size = 0)
        for (i in 0 until 40) file("deep/d$i/sub/n$i.txt")              // many dirs, nothing found

        val found = ConcurrentHashMap<String, Source>()
        val walker = Walker(parallelism = 4)
        walker.walk(listOf(tmp.root)) { f, size, _, flags ->
            Classifier.classify(f.name, size, flags) { Signature.detect(f) }?.let { found[f.name] = it.source }
        }

        assertEquals(
            mapOf(
                ".trashed-1893456000-IMG_2.jpg" to Source.TRASH,
                "abc123" to Source.BIN,
                "77.jpg" to Source.THUMBNAIL,
                "s.mp4" to Source.HIDDEN,
                "p.png" to Source.HIDDEN,
                "7ff873720154ffada211bf" to Source.CACHE,
            ),
            found.toMap(),
        )
        assertEquals(50L, walker.filesScanned.get())
    }
}
