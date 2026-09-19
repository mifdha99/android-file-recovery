package com.eslam.recovery.data.permission

import android.content.Context
import com.eslam.recovery.domain.repository.PermissionRepository

class PermissionRepositoryImpl(context: Context) : PermissionRepository {
    private val appContext = context.applicationContext
    override fun hasFileAccess(): Boolean = Permissions.hasFileAccess(appContext)
}
