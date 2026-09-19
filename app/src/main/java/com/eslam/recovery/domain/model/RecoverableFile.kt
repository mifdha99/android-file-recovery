package com.eslam.recovery.domain.model

/** Byte range inside a blob file (carved thumbnail). */
data class CarveRange(val offset: Long, val length: Int)

data class RecoverableFile(
    val id: Long,
    /** Absolute path of the file that holds the data (never modified by the app). */
    val path: String,
    /** Name shown to the user and used for the recovered copy (trash prefix stripped). */
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val kind: Kind,
    val source: Source,
    /** Epoch millis when the system trash will purge this file, if known. */
    val expiresAt: Long? = null,
    val carve: CarveRange? = null,
) {
    val type: FileType get() = kind.type
    val mime: String get() = kind.mime
}
