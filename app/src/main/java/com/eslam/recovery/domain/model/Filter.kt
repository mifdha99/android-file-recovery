package com.eslam.recovery.domain.model

enum class SortOrder { SIZE_DESC, SIZE_ASC, NEWEST, OLDEST, NAME }

/**
 * Location filter. [DELETED] groups the sources that only ever contain deleted items, as opposed to
 * hidden / cached media, which is merely invisible in the gallery and may never have been deleted.
 */
enum class SourceFilter(val sources: Set<Source>) {
    ALL(Source.entries.toSet()),
    DELETED(setOf(Source.TRASH, Source.BIN, Source.THUMBNAIL)),
    TRASH(setOf(Source.TRASH)),
    BIN(setOf(Source.BIN)),
    THUMBNAIL(setOf(Source.THUMBNAIL)),
    HIDDEN(setOf(Source.HIDDEN)),
    CACHE(setOf(Source.CACHE));

    companion object {
        fun of(source: Source): SourceFilter = entries.first { it.sources == setOf(source) }
    }
}

data class Filter(
    val type: FileType? = null,
    val source: SourceFilter = SourceFilter.ALL,
    val sort: SortOrder = SortOrder.SIZE_DESC,
)

/** Filtered + sorted list, plus per-type counts for the active source (key null = all types). */
data class FilteredResults(val items: List<RecoverableFile> = emptyList(), val counts: Map<FileType?, Int> = emptyMap())
