package com.eslam.recovery.domain.usecase

import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.Filter
import com.eslam.recovery.domain.model.Kind
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.SortOrder
import com.eslam.recovery.domain.model.Source
import com.eslam.recovery.domain.model.SourceFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterResultsUseCaseTest {
    private val filter = FilterResultsUseCase()

    private fun file(id: Long, name: String, size: Long, type: FileType, source: Source, modified: Long = id) =
        RecoverableFile(id, "/p/$name", name, size, modified, Kind(type, "x/y", ""), source)

    private val all = listOf(
        file(1, "b.jpg", 300, FileType.IMAGE, Source.TRASH),
        file(2, "a.mp4", 900, FileType.VIDEO, Source.TRASH),
        file(3, "C.jpg", 100, FileType.IMAGE, Source.CACHE),
    )

    @Test fun defaultIsLargestFirstWithCounts() {
        val r = filter(all, Filter())
        assertEquals(listOf(2L, 1L, 3L), r.items.map { it.id })
        assertEquals(mapOf(null to 3, FileType.IMAGE to 2, FileType.VIDEO to 1), r.counts)
    }

    @Test fun typeAndSourceFiltersCombine() {
        val r = filter(all, Filter(type = FileType.IMAGE, source = SourceFilter.TRASH))
        assertEquals(listOf(1L), r.items.map { it.id })
        // Counts describe the active source so the tabs stay truthful.
        assertEquals(mapOf(null to 2, FileType.IMAGE to 1, FileType.VIDEO to 1), r.counts)
    }

    @Test fun deletedGroupExcludesHiddenAndCache() {
        assertEquals(listOf(2L, 1L), filter(all, Filter(source = SourceFilter.DELETED)).items.map { it.id })
        assertEquals(SourceFilter.BIN, SourceFilter.of(Source.BIN))
    }

    @Test fun sortOrders() {
        assertEquals(listOf(3L, 1L, 2L), filter(all, Filter(sort = SortOrder.SIZE_ASC)).items.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), filter(all, Filter(sort = SortOrder.NEWEST)).items.map { it.id })
        assertEquals(listOf(2L, 1L, 3L), filter(all, Filter(sort = SortOrder.NAME)).items.map { it.id })
    }
}
