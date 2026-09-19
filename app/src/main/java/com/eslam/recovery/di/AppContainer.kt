package com.eslam.recovery.di

import android.content.Context
import com.eslam.recovery.data.DispatcherProvider
import com.eslam.recovery.data.permission.PermissionRepositoryImpl
import com.eslam.recovery.data.recovery.RecoveryRepositoryImpl
import com.eslam.recovery.data.scan.ScanRepositoryImpl
import com.eslam.recovery.domain.repository.PermissionRepository
import com.eslam.recovery.domain.repository.RecoveryRepository
import com.eslam.recovery.domain.repository.ScanRepository
import com.eslam.recovery.domain.usecase.FilterResultsUseCase
import com.eslam.recovery.domain.usecase.RecoverFilesUseCase
import com.eslam.recovery.domain.usecase.ScanStorageUseCase

/**
 * Manual dependency graph (the app is small enough that a DI framework would only add build time).
 * Holds the application context only, never an Activity, so nothing here can leak a screen.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val dispatchers = DispatcherProvider()

    private val scanRepository: ScanRepository by lazy { ScanRepositoryImpl(appContext, dispatchers) }
    private val recoveryRepository: RecoveryRepository by lazy { RecoveryRepositoryImpl(appContext, dispatchers) }
    val permissionRepository: PermissionRepository by lazy { PermissionRepositoryImpl(appContext) }

    val scanStorage get() = ScanStorageUseCase(scanRepository)
    val recoverFiles get() = RecoverFilesUseCase(recoveryRepository)
    val filterResults get() = FilterResultsUseCase()
}
