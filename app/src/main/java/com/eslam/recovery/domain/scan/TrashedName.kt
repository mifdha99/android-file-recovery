package com.eslam.recovery.domain.scan

/** Parsed MediaStore name: `.trashed-<expiryEpochSeconds>-<original>` or `.pending-<expiry>-<original>`. */
data class TrashedName(val trashed: Boolean, val expiresAtSeconds: Long, val originalName: String) {
    companion object {
        // Same pattern MediaProvider uses for its own trashed / pending files.
        private val PATTERN = Regex("""(?i)^\.(pending|trashed)-(\d+)-([^/]+)$""")

        fun parse(name: String): TrashedName? {
            if (name.length < 10 || name[0] != '.') return null
            val m = PATTERN.matchEntire(name) ?: return null
            return TrashedName(
                trashed = m.groupValues[1].equals("trashed", ignoreCase = true),
                expiresAtSeconds = m.groupValues[2].toLongOrNull() ?: return null,
                originalName = m.groupValues[3],
            )
        }
    }
}
