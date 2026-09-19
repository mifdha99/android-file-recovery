package com.eslam.recovery.domain.scan

import com.eslam.recovery.domain.model.CarveRange
import java.io.File
import java.io.RandomAccessFile

/**
 * Finds complete JPEG streams inside a blob (legacy `.thumbdata` caches). Segment-aware: a naive
 * FFD8..FFD9 search would stop at the thumbnail embedded in EXIF, so headers are walked by their
 * length fields and only the entropy-coded data is scanned for the end marker.
 */
object JpegCarver {
    private const val MIN_SIZE = 2 * 1024
    private const val MAX_SIZE = 64L * 1024 * 1024

    fun carve(file: File, onFound: (CarveRange) -> Unit = {}): List<CarveRange> {
        val out = ArrayList<CarveRange>()
        RandomAccessFile(file, "r").use { raf ->
            val r = Reader(raf)
            var pos = 0L
            while (pos < r.size - 3) {
                if (r.at(pos) == 0xFF && r.at(pos + 1) == 0xD8 && r.at(pos + 2) == 0xFF) {
                    val end = jpegEnd(r, pos)
                    if (end > 0) {
                        if (end - pos >= MIN_SIZE) CarveRange(pos, (end - pos).toInt()).let { out += it; onFound(it) }
                        pos = end
                        continue
                    }
                }
                pos++
            }
        }
        return out
    }

    /** Exclusive end offset of the JPEG starting at [start], or -1 if it is not a well-formed stream. */
    private fun jpegEnd(r: Reader, start: Long): Long {
        var p = start + 2
        while (p - start < MAX_SIZE) {
            if (r.at(p) != 0xFF) return -1
            val marker = r.at(p + 1)
            when {
                marker < 0 -> return -1
                marker == 0xFF -> p++ // fill byte
                marker == 0xD9 -> return p + 2
                marker == 0x01 || marker in 0xD0..0xD8 -> p += 2 // standalone markers
                else -> {
                    val hi = r.at(p + 2)
                    val lo = r.at(p + 3)
                    if (hi < 0 || lo < 0) return -1
                    val len = (hi shl 8) or lo
                    if (len < 2) return -1
                    p += 2 + len
                    if (marker == 0xDA) p = skipEntropy(r, p, start) ?: return -1
                }
            }
        }
        return -1
    }

    /** Skips entropy-coded data; returns the position of the next real marker. */
    private fun skipEntropy(r: Reader, from: Long, start: Long): Long? {
        var p = from
        while (p - start < MAX_SIZE) {
            val b = r.at(p)
            if (b < 0) return null
            if (b != 0xFF) { p++; continue }
            val n = r.at(p + 1)
            when {
                n < 0 -> return null
                n == 0x00 || n in 0xD0..0xD7 -> p += 2 // stuffed byte / restart marker
                n == 0xFF -> p++
                else -> return p
            }
        }
        return null
    }

    /** Windowed random-access reader; access is almost always sequential so one 1 MiB window suffices. */
    private class Reader(private val raf: RandomAccessFile) {
        val size: Long = raf.length()
        private val buf = ByteArray(1 shl 20)
        private var bufStart = 0L
        private var bufLen = 0

        fun at(pos: Long): Int {
            if (pos < 0 || pos >= size) return -1
            if (pos < bufStart || pos >= bufStart + bufLen) {
                raf.seek(pos)
                bufStart = pos
                bufLen = raf.read(buf).coerceAtLeast(0)
                if (bufLen == 0) return -1
            }
            return buf[(pos - bufStart).toInt()].toInt() and 0xFF
        }
    }
}
