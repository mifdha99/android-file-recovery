package com.eslam.recovery.data.scan

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object StorageRoots {
    /** Root directory of every mounted shared-storage volume: internal storage plus SD cards / USB. */
    fun all(context: Context): List<File> {
        val roots = LinkedHashSet<File>()
        roots += primary()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = context.getSystemService(StorageManager::class.java)
            sm.storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
                .mapNotNullTo(roots) { it.directory }
        } else {
            // <volume>/Android/data/<pkg>/files → <volume>
            context.getExternalFilesDirs(null).filterNotNull()
                .mapTo(roots) { File(it.path.substringBefore("/Android/")) }
        }
        return roots.filter { it.isDirectory }
    }

    @Suppress("DEPRECATION")
    fun primary(): File = Environment.getExternalStorageDirectory()
}
