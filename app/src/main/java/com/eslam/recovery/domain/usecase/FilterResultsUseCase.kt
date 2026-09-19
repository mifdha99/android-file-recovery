package com.eslam.recovery.domain.usecase

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.Filter
import com.eslam.recovery.domain.model.FilteredResults
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.SortOrder
import com.eslam.recovery.domain.model.SourceFilter

class FilterResultsUseCase {
    operator fun invoke(all: List<RecoverableFile>, filter: Filter): FilteredResults {
        val bySource = if (filter.source == SourceFilter.ALL) all else all.filter { it.source in filter.source.sources }
        val counts = HashMap<FileType?, Int>()
        counts[null] = bySource.size
        bySource.forEach { counts.merge(it.type, 1, Int::plus) }

        val byType = if (filter.type == null) bySource else bySource.filter { it.type == filter.type }
        val sorted = when (filter.sort) {
            SortOrder.SIZE_DESC -> byType.sortedByDescending { it.size }
            SortOrder.SIZE_ASC -> byType.sortedBy { it.size }
            SortOrder.NEWEST -> byType.sortedByDescending { it.lastModified }
            SortOrder.OLDEST -> byType.sortedBy { it.lastModified }
            SortOrder.NAME -> byType.sortedBy { it.displayName.lowercase() }
        }
        return FilteredResults(sorted, counts)
    }
}
