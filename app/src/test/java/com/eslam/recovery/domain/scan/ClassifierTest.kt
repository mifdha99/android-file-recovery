package com.eslam.recovery.domain.scan

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.Kind
import com.eslam.recovery.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    private val jpeg = Kind(FileType.IMAGE, "image/jpeg", "jpg")
    private val noSniff: () -> Kind? = { error("must not read the file") }
    private val big = 500_000L

    @Test fun normalVisibleFileIsIgnoredWithoutIo() =
        assertNull(Classifier.classify("IMG_1.jpg", big, 0, noSniff))

    @Test fun trashedWinsEverywhere() {
        val m = Classifier.classify(".trashed-1893456000-IMG_1.jpg", big, 0, noSniff)!!
        assertEquals(Source.TRASH, m.source)
        assertEquals("IMG_1.jpg", m.displayName)
        assertEquals(1893456000_000L, m.expiresAt)
        assertEquals(Source.TRASH, Classifier.classify(".trashed-1-a.jpg", big, DirFlags.BIN or DirFlags.HIDDEN, noSniff)!!.source)
    }

    @Test fun binKeepsEverythingAndSniffsUnknownNames() {
        val m = Classifier.classify("0a7ff87372", big, DirFlags.BIN or DirFlags.HIDDEN) { jpeg }!!
        assertEquals(Source.BIN, m.source)
        assertEquals(FileType.IMAGE, m.kind.type)
        assertEquals(FileType.OTHER, Classifier.classify("blob", big, DirFlags.BIN) { null }!!.kind.type)
        assertEquals(Source.BIN, Classifier.classify("FSCK0001", big, DirFlags.LOST) { jpeg }!!.source)
    }

    @Test fun thumbnailsBeatHiddenAndCarryId() {
        val m = Classifier.classify("12345.jpg", 20_000, DirFlags.THUMBS, noSniff)!!
        assertEquals(Source.THUMBNAIL, m.source)
        assertEquals(12345L, m.thumbId)
        assertTrue(Classifier.classify(".thumbdata3--1967290299", big, DirFlags.THUMBS, noSniff)!!.carveBlob)
    }

    @Test fun hiddenNeedsRecognisedMediaAboveFloor() {
        assertEquals(Source.HIDDEN, Classifier.classify("a.jpg", big, DirFlags.HIDDEN, noSniff)!!.source)
        assertNull(Classifier.classify("icon.png", 1_000, DirFlags.HIDDEN, noSniff))
        assertNull(Classifier.classify("data.db", big, DirFlags.HIDDEN) { null })
        assertNull(Classifier.classify("pack.zip", big, DirFlags.HIDDEN, noSniff))
        assertEquals(Source.HIDDEN, Classifier.classify(".secret.mp4", big, 0, noSniff)!!.source)
        val pending = Classifier.classify(".pending-1700000000-clip.mp4", big, 0, noSniff)!!
        assertEquals(Source.HIDDEN, pending.source)
        assertEquals("clip.mp4", pending.displayName)
    }

    @Test fun cacheRules() {
        // Hash-named file anywhere under Android/ : sniffed.
        assertEquals(Source.CACHE, Classifier.classify("7ff873720154ffada211bf", big, DirFlags.ANDROID) { jpeg }!!.source)
        // A normal named photo under Android/media is a visible file, not cache.
        assertNull(Classifier.classify("IMG-WA0001.jpg", big, DirFlags.ANDROID, noSniff))
        // Inside a cache dir a named image counts too.
        assertEquals(Source.CACHE, Classifier.classify("x.jpg", big, DirFlags.CACHE, noSniff)!!.source)
        assertNull(Classifier.classify("small", 100, DirFlags.CACHE, noSniff))
        assertNull(Classifier.classify("doc", big, DirFlags.CACHE) { Kind(FileType.DOCUMENT, "application/pdf", "pdf") })
    }

    @Test fun flags() {
        assertTrue(DirFlags.childFlags(0, ".Trash", "Android", 1) and DirFlags.BIN != 0)
        assertTrue(DirFlags.childFlags(0, ".ColorOSGalleryRecycler", "0", 0) and DirFlags.BIN != 0)
        assertTrue(DirFlags.childFlags(0, "LOST.DIR", "0", 0) and DirFlags.LOST != 0)
        assertTrue(DirFlags.childFlags(0, "Android", "0", 0) and DirFlags.ANDROID != 0)
        assertEquals(0, DirFlags.childFlags(0, "Android", "x", 2))
        assertTrue(DirFlags.childFlags(0, "image_manager_disk_cache", "x", 3) and DirFlags.CACHE != 0)
        val thumbs = DirFlags.childFlags(0, ".thumbnails", "Pictures", 1)
        assertTrue(thumbs and DirFlags.THUMBS != 0)
        assertEquals(0, thumbs and DirFlags.HIDDEN)
        assertTrue(DirFlags.childFlags(0, ".Statuses", "Media", 4) and DirFlags.HIDDEN != 0)
        assertEquals(DirFlags.BIN, DirFlags.childFlags(DirFlags.BIN, "sub", ".Trash", 3))
        assertTrue(DirFlags.shouldSkip(".thumbnails", "Music"))
    }
}
