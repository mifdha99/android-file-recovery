package com.eslam.recovery.domain.scan

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.Kind
import java.io.File
import java.io.FileInputStream

/** Magic-byte detection for files whose name says nothing (recycle bins, caches, LOST.DIR). */
object Signature {
    const val HEADER_SIZE = 16

    fun detect(file: File): Kind? = try {
        val header = ByteArray(HEADER_SIZE)
        val read = FileInputStream(file).use { it.read(header) }
        if (read <= 0) null else detect(header, read)
    } catch (_: Exception) {
        null
    }

    fun detect(h: ByteArray, length: Int = h.size): Kind? {
        if (length < 4) return null
        return when {
            h.starts(length, 0xFF, 0xD8, 0xFF) -> Kind(FileType.IMAGE, "image/jpeg", "jpg")
            h.starts(length, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Kind(FileType.IMAGE, "image/png", "png")
            h.ascii(length, 0, "GIF8") -> Kind(FileType.IMAGE, "image/gif", "gif")
            h.ascii(length, 0, "RIFF") && h.ascii(length, 8, "WEBP") -> Kind(FileType.IMAGE, "image/webp", "webp")
            h.ascii(length, 0, "RIFF") && h.ascii(length, 8, "AVI ") -> Kind(FileType.VIDEO, "video/x-msvideo", "avi")
            h.ascii(length, 0, "RIFF") && h.ascii(length, 8, "WAVE") -> Kind(FileType.AUDIO, "audio/x-wav", "wav")
            h.ascii(length, 4, "ftyp") -> ftyp(h, length)
            h.starts(length, 0x1A, 0x45, 0xDF, 0xA3) -> Kind(FileType.VIDEO, "video/x-matroska", "mkv")
            h.ascii(length, 0, "ID3") -> Kind(FileType.AUDIO, "audio/mpeg", "mp3")
            h.ascii(length, 0, "OggS") -> Kind(FileType.AUDIO, "audio/ogg", "ogg")
            h.ascii(length, 0, "fLaC") -> Kind(FileType.AUDIO, "audio/flac", "flac")
            h.ascii(length, 0, "#!AMR") -> Kind(FileType.AUDIO, "audio/amr", "amr")
            h.ascii(length, 0, "%PDF") -> Kind(FileType.DOCUMENT, "application/pdf", "pdf")
            h.starts(length, 0x50, 0x4B, 0x03, 0x04) -> Kind(FileType.ARCHIVE, "application/zip", "zip")
            h.ascii(length, 0, "Rar!") -> Kind(FileType.ARCHIVE, "application/vnd.rar", "rar")
            h.starts(length, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> Kind(FileType.ARCHIVE, "application/x-7z-compressed", "7z")
            h.starts(length, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> Kind(FileType.DOCUMENT, "application/msword", "doc")
            // BMP last: two-byte magic, so also require a plausible header (reserved bytes are zero).
            h.ascii(length, 0, "BM") && length >= 10 && h[6] == 0.toByte() && h[7] == 0.toByte() &&
                h[8] == 0.toByte() && h[9] == 0.toByte() -> Kind(FileType.IMAGE, "image/bmp", "bmp")
            else -> null
        }
    }

    private fun ftyp(h: ByteArray, length: Int): Kind? {
        if (length < 12) return null
        val brand = String(h, 8, 4, Charsets.US_ASCII)
        return when {
            brand == "avif" || brand == "avis" -> Kind(FileType.IMAGE, "image/avif", "avif")
            brand in HEIC_BRANDS -> Kind(FileType.IMAGE, "image/heic", "heic")
            brand == "qt  " -> Kind(FileType.VIDEO, "video/quicktime", "mov")
            brand.startsWith("3g") -> Kind(FileType.VIDEO, "video/3gpp", "3gp")
            brand == "M4A " || brand == "M4B " -> Kind(FileType.AUDIO, "audio/mp4", "m4a")
            else -> Kind(FileType.VIDEO, "video/mp4", "mp4")
        }
    }

    private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")

    private fun ByteArray.starts(length: Int, vararg magic: Int): Boolean {
        if (length < magic.size) return false
        for (i in magic.indices) if ((this[i].toInt() and 0xFF) != magic[i]) return false
        return true
    }

    private fun ByteArray.ascii(length: Int, offset: Int, text: String): Boolean {
        if (length < offset + text.length) return false
        for (i in text.indices) if (this[offset + i] != text[i].code.toByte()) return false
        return true
    }
}
