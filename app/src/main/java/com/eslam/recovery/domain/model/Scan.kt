package com.eslam.recovery.domain.model

data class ScanProgress(
    val dirs: Long = 0,
    val files: Long = 0,
    val found: Long = 0,
    val foundBytes: Long = 0,
    /** Walk finished; thumbnails and blobs are being resolved. */
    val finishing: Boolean = false,
)

sealed interface ScanUpdate {
    data class Progress(val progress: ScanProgress) : ScanUpdate
    /** Terminal event. Files are ordered biggest first. */
    data class Finished(val files: List<RecoverableFile>) : ScanUpdate
}
