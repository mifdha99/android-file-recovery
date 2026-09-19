package com.eslam.recovery.domain.scan

/** Bit flags inherited down the directory tree so every file is classified in O(1) without path parsing. */
object DirFlags {
    const val BIN = 1
    const val HIDDEN = 2
    const val THUMBS = 4
    const val LOST = 8
    /** Inside a cache-named directory: media with or without extension counts. */
    const val CACHE = 16
    /** Inside the top-level Android/ tree: only extension-less (hash-named) media counts. */
    const val ANDROID = 32

    private val BIN_WORDS = listOf("trash", "recycle", "dustbin", "garbage")
    private val THUMB_PARENTS = setOf("Pictures", "Movies", "DCIM")

    /** Flags for directory [name] whose parent has [parent] flags. [depth] 0 = directly under a storage root. */
    fun childFlags(parent: Int, name: String, parentName: String, depth: Int): Int {
        var f = parent
        val n = name.lowercase()
        if (BIN_WORDS.any { it in n }) f = f or BIN
        if (name == "LOST.DIR") f = f or LOST
        if (depth == 0 && name == "Android") f = f or ANDROID
        if (n.trimStart('.').let { it.startsWith("cache") || it.endsWith("cache") }) f = f or CACHE
        if (name == ".thumbnails" && parentName in THUMB_PARENTS) f = f or THUMBS
        else if (name.startsWith(".")) f = f or HIDDEN
        return f
    }

    /** Directories that only produce noise. Music/.thumbnails is album art keyed by album id. */
    fun shouldSkip(name: String, parentName: String): Boolean =
        name == ".thumbnails" && parentName == "Music"
}
