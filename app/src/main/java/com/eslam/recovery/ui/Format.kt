package com.eslam.recovery.ui

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.SortOrder
import com.eslam.recovery.domain.model.Source
import com.eslam.recovery.domain.model.SourceFilter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[i])
}

fun formatDate(millis: Long): String =
    if (millis <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun formatCount(n: Long): String = String.format(Locale.US, "%,d", n)

/** "Deleted from device in 12 days" for system-trash items. */
fun expiryText(expiresAt: Long?): String? {
    expiresAt ?: return null
    val days = TimeUnit.MILLISECONDS.toDays(expiresAt - System.currentTimeMillis())
    return when {
        days < 0 -> "Trash expiry passed — recover now"
        days == 0L -> "Erased from trash today — recover now"
        else -> "Erased from trash in $days days"
    }
}

val Source.label: String
    get() = when (this) {
        Source.TRASH -> "System trash"
        Source.BIN -> "Recycle bin"
        Source.THUMBNAIL -> "Thumbnail"
        Source.HIDDEN -> "Hidden"
        Source.CACHE -> "App cache"
    }

val Source.description: String
    get() = when (this) {
        Source.TRASH -> "Deleted in the last 30 days, full quality"
        Source.BIN -> "Gallery / file manager recycle bins, full quality"
        Source.THUMBNAIL -> "Low-resolution copies of deleted photos"
        Source.HIDDEN -> "Media hidden from the gallery"
        Source.CACHE -> "Cached copies kept by apps"
    }

val FileType?.label: String
    get() = when (this) {
        null -> "All"
        FileType.IMAGE -> "Images"
        FileType.VIDEO -> "Videos"
        FileType.AUDIO -> "Audio"
        FileType.DOCUMENT -> "Documents"
        FileType.ARCHIVE -> "Archives"
        FileType.OTHER -> "Other"
    }

val SortOrder.label: String
    get() = when (this) {
        SortOrder.SIZE_DESC -> "Largest first"
        SortOrder.SIZE_ASC -> "Smallest first"
        SortOrder.NEWEST -> "Newest first"
        SortOrder.OLDEST -> "Oldest first"
        SortOrder.NAME -> "Name"
    }

val SourceFilter.label: String
    get() = when (this) {
        SourceFilter.ALL -> "All locations"
        SourceFilter.DELETED -> "Deleted only"
        else -> sources.first().label
    }
