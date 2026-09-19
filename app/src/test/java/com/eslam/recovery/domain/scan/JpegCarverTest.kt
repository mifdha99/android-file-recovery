package com.eslam.recovery.domain.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class JpegCarverTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Synthetic JPEG: SOI, APP1 that embeds a full inner JPEG (the EXIF-thumbnail trap), SOS, stuffed entropy data, EOI. */
    private fun jpeg(seed: Int, entropySize: Int): ByteArray {
        val o = ByteArrayOutputStream()
        fun w(vararg b: Int) = b.forEach { o.write(it) }
        w(0xFF, 0xD8)
        val inner = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0, 2, 0xFF.toByte(), 0xD9.toByte())
        w(0xFF, 0xE1, 0, 2 + inner.size); o.write(inner)
        w(0xFF, 0xDA, 0, 4, 1, 2)
        val rnd = Random(seed)
        repeat(entropySize) {
            val b = rnd.nextInt(256)
            o.write(b)
            if (b == 0xFF) o.write(if (rnd.nextBoolean()) 0x00 else 0xD3)
        }
        w(0xFF, 0xD9)
        return o.toByteArray()
    }

    @Test fun carvesWholeImagesFromBlob() {
        val a = jpeg(1, 5_000)
        val b = jpeg(2, 9_000)
        val tiny = jpeg(3, 100) // under the minimum size: ignored
        val junk = ByteArray(3_000) { 0x11 }
        val blob = tmp.newFile(".thumbdata3--1967290299")
        blob.writeBytes(junk + a + junk + tiny + byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00) + b + junk)

        val found = JpegCarver.carve(blob)
        assertEquals(2, found.size)
        val all = blob.readBytes()
        assertArrayEquals(a, all.copyOfRange(found[0].offset.toInt(), found[0].offset.toInt() + found[0].length))
        assertArrayEquals(b, all.copyOfRange(found[1].offset.toInt(), found[1].offset.toInt() + found[1].length))
    }

    @Test fun truncatedImageIsIgnored() {
        val blob = tmp.newFile("blob")
        blob.writeBytes(jpeg(4, 5_000).copyOf(3_000))
        assertEquals(0, JpegCarver.carve(blob).size)
    }
}
