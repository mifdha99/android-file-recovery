package com.eslam.recovery.data.recovery

import com.eslam.recovery.data.scan.StorageRoots
import com.eslam.recovery.domain.model.FileType
import java.io.File

/** Where recovered copies go: `<internal storage>/RecoveredFiles/<type folder>/`. */
object RecoveryDestination {
    fun root(): File = File(StorageRoots.primary(), "RecoveredFiles")

    fun folderFor(type: FileType): String = when (type) {
        FileType.IMAGE -> "Images"
        FileType.VIDEO -> "Videos"
        FileType.AUDIO -> "Audio"
        FileType.DOCUMENT -> "Documents"
        FileType.ARCHIVE, FileType.OTHER -> "Other"
    }
}
