package com.eslam.recovery.ui

import com.eslam.recovery.data.DispatcherProvider
import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.Kind
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.RecoveryProgress
import com.eslam.recovery.domain.model.RecoverySummary
import com.eslam.recovery.domain.model.RecoveryUpdate
import com.eslam.recovery.domain.model.ScanProgress
import com.eslam.recovery.domain.model.ScanUpdate
import com.eslam.recovery.domain.model.Source
import com.eslam.recovery.domain.repository.PermissionRepository
import com.eslam.recovery.domain.repository.RecoveryRepository
import com.eslam.recovery.domain.repository.ScanRepository
import com.eslam.recovery.domain.usecase.FilterResultsUseCase
import com.eslam.recovery.domain.usecase.RecoverFilesUseCase
import com.eslam.recovery.domain.usecase.ScanStorageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val files = listOf(
        RecoverableFile(1, "/a", "a.jpg", 100, 0, Kind(FileType.IMAGE, "image/jpeg", "jpg"), Source.TRASH),
        RecoverableFile(2, "/b", "b.mp4", 900, 0, Kind(FileType.VIDEO, "video/mp4", "mp4"), Source.BIN),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(scan: Flow<ScanUpdate>, recovered: MutableList<List<RecoverableFile>> = mutableListOf()) = MainViewModel(
        scanStorage = ScanStorageUseCase(object : ScanRepository { override fun scan() = scan }),
        recoverFiles = RecoverFilesUseCase(object : RecoveryRepository {
            override fun recover(items: List<RecoverableFile>) = flow {
                recovered += items
                emit(RecoveryUpdate.Progress(RecoveryProgress(0, items.size, 0, 0)))
                emit(RecoveryUpdate.Finished(RecoverySummary(items.mapTo(HashSet()) { it.id }, emptyList(), "/out")))
            }
        }),
        filterResults = FilterResultsUseCase(),
        permissions = object : PermissionRepository { override fun hasFileAccess() = true },
        dispatchers = DispatcherProvider(io = dispatcher, default = dispatcher),
    )

    @Test fun scanPublishesResultsLargestFirst() = runTest(dispatcher) {
        val vm = viewModel(flow { emit(ScanUpdate.Progress(ScanProgress(found = 1))); emit(ScanUpdate.Finished(files)) })
        vm.startScan()
        advanceUntilIdle()
        assertEquals(ScanState.Done(2, 1000), vm.scan.value)
        assertEquals(listOf(2L, 1L), vm.visible.value.items.map { it.id })
    }

    @Test fun cancelledScanNeverReturnsToRunning() = runTest(dispatcher) {
        val vm = viewModel(flow { emit(ScanUpdate.Progress(ScanProgress(files = 5))); awaitCancellation() })
        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.scan.value is ScanState.Running)
        vm.cancelScan()
        advanceUntilIdle()
        assertEquals(ScanState.Idle, vm.scan.value)
    }

    @Test fun selectionSummaryAndRecovery() = runTest(dispatcher) {
        val recovered = mutableListOf<List<RecoverableFile>>()
        val vm = viewModel(flow { emit(ScanUpdate.Finished(files)) }, recovered)
        vm.startScan(); advanceUntilIdle()

        vm.toggleSelectAll(); advanceUntilIdle()
        assertEquals(SelectionSummary(2, 1000), vm.selectionSummary.value)
        vm.toggle(1); advanceUntilIdle()
        assertEquals(SelectionSummary(1, 900), vm.selectionSummary.value)

        vm.recoverSelected(); advanceUntilIdle()
        assertEquals(listOf(2L), recovered.single().map { it.id })
        assertEquals(setOf(2L), vm.recovered.value)
        assertTrue(vm.selection.value.isEmpty())
        assertTrue(vm.recovery.value is RecoveryState.Done)
    }
}
