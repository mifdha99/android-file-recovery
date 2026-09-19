package com.eslam.recovery.domain.repository

import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.RecoveryUpdate
import com.eslam.recovery.domain.model.ScanUpdate
import kotlinx.coroutines.flow.Flow

interface ScanRepository {
    /** Cold flow: collecting starts a scan, cancelling the collector stops it. Ends with [ScanUpdate.Finished]. */
    fun scan(): Flow<ScanUpdate>
}

interface RecoveryRepository {
    /** Cold flow: copies [items] out; source files are never modified. Ends with [RecoveryUpdate.Finished]. */
    fun recover(items: List<RecoverableFile>): Flow<RecoveryUpdate>
}

interface PermissionRepository {
    /** True when the app may read all shared storage. */
    fun hasFileAccess(): Boolean
}
