package com.eslam.recovery.domain.usecase

import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.RecoveryUpdate
import com.eslam.recovery.domain.repository.RecoveryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class RecoverFilesUseCase(private val repository: RecoveryRepository) {
    operator fun invoke(items: List<RecoverableFile>): Flow<RecoveryUpdate> =
        if (items.isEmpty()) emptyFlow() else repository.recover(items)
}
