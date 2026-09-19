package com.eslam.recovery.domain.usecase

import com.eslam.recovery.domain.model.ScanUpdate
import com.eslam.recovery.domain.repository.ScanRepository
import kotlinx.coroutines.flow.Flow

class ScanStorageUseCase(private val repository: ScanRepository) {
    operator fun invoke(): Flow<ScanUpdate> = repository.scan()
}
