package com.eslam.recovery.domain.scan

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureTest {
    private fun bytes(vararg v: Int) = ByteArray(16).also { a -> v.forEachIndexed { i, b -> a[i] = b.toByte() } }
    private fun ascii(text: String, offset: Int = 0, base: ByteArray = ByteArray(16)) =
        base.also { a -> text.forEachIndexed { i, c -> a[offset + i] = c.code.toByte() } }

    @Test fun jpeg() = assertEquals("image/jpeg", Signature.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))?.mime)
    @Test fun png() = assertEquals("image/png", Signature.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))?.mime)
    @Test fun webp() = assertEquals("image/webp", Signature.detect(ascii("WEBP", 8, ascii("RIFF")))?.mime)
    @Test fun wav() = assertEquals(FileType.AUDIO, Signature.detect(ascii("WAVE", 8, ascii("RIFF")))?.type)
    @Test fun mp4() = assertEquals("video/mp4", Signature.detect(ascii("isom", 8, ascii("ftyp", 4)))?.mime)
    @Test fun heic() = assertEquals("image/heic", Signature.detect(ascii("heic", 8, ascii("ftyp", 4)))?.mime)
    @Test fun m4a() = assertEquals(FileType.AUDIO, Signature.detect(ascii("M4A ", 8, ascii("ftyp", 4)))?.type)
    @Test fun mov() = assertEquals("video/quicktime", Signature.detect(ascii("qt  ", 8, ascii("ftyp", 4)))?.mime)
    @Test fun pdf() = assertEquals("application/pdf", Signature.detect(ascii("%PDF-1.7"))?.mime)
    @Test fun zip() = assertEquals(FileType.ARCHIVE, Signature.detect(bytes(0x50, 0x4B, 0x03, 0x04))?.type)
    @Test fun mkv() = assertEquals(FileType.VIDEO, Signature.detect(bytes(0x1A, 0x45, 0xDF, 0xA3))?.type)
    @Test fun unknownIsNull() = assertNull(Signature.detect(ascii("hello world 1234")))
    @Test fun zerosAreNull() = assertNull(Signature.detect(ByteArray(16)))
    @Test fun shortHeaderIsNull() = assertNull(Signature.detect(bytes(0xFF, 0xD8, 0xFF), 3))

    @Test fun extensionLookup() {
        assertEquals(FileType.IMAGE, FileTypes.fromName("IMG_1.JPG")?.type)
        assertEquals(FileType.VIDEO, FileTypes.fromName("a.b.mp4")?.type)
        assertNull(FileTypes.fromName("0a7ff873720154ffada211bf"))
        assertNull(FileTypes.fromName(".hidden"))
        assertNull(FileTypes.fromName("cache.0"))
    }
}
