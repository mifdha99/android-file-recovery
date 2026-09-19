package com.eslam.recovery.data.scan

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi

class MediaStoreSource(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    data class TrashedRow(val path: String, val size: Long, val modifiedMillis: Long, val expiresMillis: Long?)

    /**
     * Ids of every row MediaStore currently knows (including trashed and pending ones). A thumbnail
     * whose id is missing from this set belongs to media that no longer exists. Null when the set
     * cannot be trusted — callers must then skip orphan detection instead of flagging everything.
     */
    fun currentIds(): Set<Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val ids = HashSet<Long>()
            val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), includeAllArgs(), null)
            } else {
                @Suppress("DEPRECATION")
                resolver.query(MediaStore.setIncludePending(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            }
            cursor?.use { c -> while (c.moveToNext()) ids += c.getLong(0) } ?: return null
            ids.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** Trashed rows as MediaStore sees them. Supplement to the filesystem walk; empty on any failure. */
    fun trashed(): List<TrashedRow> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY) }
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_EXPIRES,
            )
            val rows = ArrayList<TrashedRow>()
            resolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    rows += TrashedRow(
                        path = path,
                        size = c.getLong(1),
                        modifiedMillis = c.getLong(2) * 1000,
                        expiresMillis = if (c.isNull(3)) null else c.getLong(3) * 1000,
                    )
                }
            }
            rows
        } catch (_: Exception) {
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun includeAllArgs() = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
    }
}
