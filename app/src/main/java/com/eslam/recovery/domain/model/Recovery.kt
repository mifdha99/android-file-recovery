package com.eslam.recovery.domain.model

data class RecoveryProgress(val doneFiles: Int, val totalFiles: Int, val doneBytes: Long, val totalBytes: Long)

data class RecoveryFailure(val fileName: String, val reason: String)

data class RecoverySummary(
    val recoveredIds: Set<Long>,
    val failures: List<RecoveryFailure>,
    val destination: String,
    /** Set when nothing could be attempted at all (no space, destination not writable). */
    val error: String? = null,
)

sealed interface RecoveryUpdate {
    data class Progress(val progress: RecoveryProgress) : RecoveryUpdate
    data class Finished(val summary: RecoverySummary) : RecoveryUpdate
}
