package com.eslam.recovery.domain.model

enum class FileType { IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER }

/** Where a candidate was found. Order = display order. */
enum class Source {
    /** MediaStore trash: `.trashed-<expiry>-<name>`, full quality, kept 30 days. */
    TRASH,
    /** Vendor gallery / file-manager recycle bin or LOST.DIR, full quality. */
    BIN,
    /** Thumbnail of media that no longer exists (low resolution). */
    THUMBNAIL,
    /** Media hidden from the gallery (dot-dirs, .nomedia, pending files). */
    HIDDEN,
    /** Extension-less cached copies under Android/ or cache dirs. Not guaranteed deleted. */
    CACHE,
}

/** Detected kind of a file. [extension] has no dot and is what gets appended when the name lacks one. */
data class Kind(val type: FileType, val mime: String, val extension: String)

object FileTypes {
    private val byExtension: Map<String, Kind> = buildMap {
        fun add(type: FileType, mime: String, vararg exts: String) =
            exts.forEach { put(it, Kind(type, mime, it)) }

        add(FileType.IMAGE, "image/jpeg", "jpg", "jpeg")
        add(FileType.IMAGE, "image/png", "png")
        add(FileType.IMAGE, "image/gif", "gif")
        add(FileType.IMAGE, "image/webp", "webp")
        add(FileType.IMAGE, "image/bmp", "bmp")
        add(FileType.IMAGE, "image/heic", "heic", "heif")
        add(FileType.IMAGE, "image/avif", "avif")
        add(FileType.IMAGE, "image/x-adobe-dng", "dng")

        add(FileType.VIDEO, "video/mp4", "mp4", "m4v")
        add(FileType.VIDEO, "video/quicktime", "mov")
        add(FileType.VIDEO, "video/3gpp", "3gp")
        add(FileType.VIDEO, "video/x-matroska", "mkv")
        add(FileType.VIDEO, "video/webm", "webm")
        add(FileType.VIDEO, "video/x-msvideo", "avi")
        add(FileType.VIDEO, "video/mp2ts", "ts")

        add(FileType.AUDIO, "audio/mpeg", "mp3")
        add(FileType.AUDIO, "audio/mp4", "m4a")
        add(FileType.AUDIO, "audio/aac", "aac")
        add(FileType.AUDIO, "audio/x-wav", "wav")
        add(FileType.AUDIO, "audio/ogg", "ogg", "opus")
        add(FileType.AUDIO, "audio/flac", "flac")
        add(FileType.AUDIO, "audio/amr", "amr")

        add(FileType.DOCUMENT, "application/pdf", "pdf")
        add(FileType.DOCUMENT, "application/msword", "doc")
        add(FileType.DOCUMENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
        add(FileType.DOCUMENT, "application/vnd.ms-excel", "xls")
        add(FileType.DOCUMENT, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
        add(FileType.DOCUMENT, "application/vnd.ms-powerpoint", "ppt")
        add(FileType.DOCUMENT, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx")
        add(FileType.DOCUMENT, "text/plain", "txt")
        add(FileType.DOCUMENT, "text/csv", "csv")

        add(FileType.ARCHIVE, "application/zip", "zip")
        add(FileType.ARCHIVE, "application/vnd.rar", "rar")
        add(FileType.ARCHIVE, "application/x-7z-compressed", "7z")
        add(FileType.ARCHIVE, "application/vnd.android.package-archive", "apk")
    }

    val UNKNOWN = Kind(FileType.OTHER, "application/octet-stream", "")

    /** Lookup by file name, no I/O. Null when the extension is missing or not recognised. */
    fun fromName(name: String): Kind? {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        return byExtension[name.substring(dot + 1).lowercase()]
    }
}
