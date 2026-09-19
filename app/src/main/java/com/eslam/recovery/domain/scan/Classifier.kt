package com.eslam.recovery.domain.scan

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.FileTypes
import com.eslam.recovery.domain.model.Kind
import com.eslam.recovery.domain.model.Source
/** Result of classifying one file. */
data class Match(
    val source: Source,
    val kind: Kind,
    val displayName: String,
    val expiresAt: Long? = null,
    /** MediaStore id encoded in a thumbnail name; the file only counts if that id no longer exists. */
    val thumbId: Long? = null,
    /** Legacy `.thumbdata` blob: JPEGs must be carved out of it. */
    val carveBlob: Boolean = false,
)

object Classifier {
    /** Below this, hidden/cache images are icons, stickers and UI sprites, not photos. */
    const val MIN_MEDIA_SIZE = 8 * 1024L

    /**
     * First match wins: trashed name → recycle bin / LOST.DIR → thumbnails → hidden → cache.
     * Returns null for normal visible files. [sniff] reads magic bytes and is only invoked when the
     * name does not identify the type, so most files cost no I/O.
     */
    fun classify(name: String, size: Long, flags: Int, sniff: () -> Kind?): Match? {
        if (size <= 0 || name == ".nomedia") return null

        val parsed = TrashedName.parse(name)
        if (parsed != null && parsed.trashed) {
            val kind = FileTypes.fromName(parsed.originalName) ?: sniff() ?: FileTypes.UNKNOWN
            return Match(Source.TRASH, kind, parsed.originalName, expiresAt = parsed.expiresAtSeconds * 1000)
        }
        val displayName = parsed?.originalName ?: name

        if (flags and (DirFlags.BIN or DirFlags.LOST) != 0) {
            val kind = FileTypes.fromName(displayName) ?: sniff() ?: FileTypes.UNKNOWN
            return Match(Source.BIN, kind, displayName)
        }

        if (flags and DirFlags.THUMBS != 0) {
            if (name.startsWith(".thumbdata")) {
                return Match(Source.THUMBNAIL, JPEG, name, carveBlob = true)
            }
            if (size < MIN_MEDIA_SIZE / 4) return null
            val kind = FileTypes.fromName(name) ?: sniff() ?: return null
            if (kind.type != FileType.IMAGE) return null
            return Match(Source.THUMBNAIL, kind, name, thumbId = name.substringBeforeLast('.').toLongOrNull())
        }

        val hidden = flags and DirFlags.HIDDEN != 0 || name.startsWith(".")
        if (hidden) {
            val kind = mediaKind(displayName, size, sniff) ?: return null
            if (kind.type == FileType.ARCHIVE || kind.type == FileType.OTHER) return null
            return Match(Source.HIDDEN, kind, displayName)
        }

        if (flags and (DirFlags.CACHE or DirFlags.ANDROID) != 0) {
            val named = FileTypes.fromName(name)
            // Under plain Android/ a file with a real extension is a normal visible file, not a cache entry.
            if (named != null && flags and DirFlags.CACHE == 0) return null
            val kind = named ?: mediaKind(name, size, sniff) ?: return null
            if (kind.type != FileType.IMAGE && kind.type != FileType.VIDEO) return null
            if (size < MIN_MEDIA_SIZE) return null
            return Match(Source.CACHE, kind, name)
        }
        return null
    }

    private fun mediaKind(name: String, size: Long, sniff: () -> Kind?): Kind? {
        val named = FileTypes.fromName(name)
        if (named != null) {
            return if (named.type == FileType.IMAGE && size < MIN_MEDIA_SIZE) null else named
        }
        if (size < MIN_MEDIA_SIZE) return null
        return sniff()
    }

    private val JPEG = Kind(FileType.IMAGE, "image/jpeg", "jpg")
}
