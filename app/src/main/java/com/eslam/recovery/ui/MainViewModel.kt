package com.eslam.recovery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eslam.recovery.App
import com.eslam.recovery.data.DispatcherProvider
import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.Filter
import com.eslam.recovery.domain.model.FilteredResults
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.RecoveryProgress
import com.eslam.recovery.domain.model.RecoverySummary
import com.eslam.recovery.domain.model.RecoveryUpdate
import com.eslam.recovery.domain.model.ScanProgress
import com.eslam.recovery.domain.model.ScanUpdate
import com.eslam.recovery.domain.model.SortOrder
import com.eslam.recovery.domain.model.SourceFilter
import com.eslam.recovery.domain.repository.PermissionRepository
import com.eslam.recovery.domain.usecase.FilterResultsUseCase
import com.eslam.recovery.domain.usecase.RecoverFilesUseCase
import com.eslam.recovery.domain.usecase.ScanStorageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val progress: ScanProgress) : ScanState
    data class Done(val total: Int, val totalBytes: Long) : ScanState
}

sealed interface RecoveryState {
    data object Idle : RecoveryState
    data class Running(val progress: RecoveryProgress) : RecoveryState
    data class Done(val summary: RecoverySummary) : RecoveryState
}

data class SelectionSummary(val count: Int = 0, val bytes: Long = 0)

/**
 * Single state holder shared by all screens. Depends only on domain use cases, holds no Context,
 * View or Activity, and every coroutine runs in [viewModelScope], so nothing outlives the screen.
 */
class MainViewModel(
    private val scanStorage: ScanStorageUseCase,
    private val recoverFiles: RecoverFilesUseCase,
    private val filterResults: FilterResultsUseCase,
    private val permissions: PermissionRepository,
    dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _hasAccess = MutableStateFlow(permissions.hasFileAccess())
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _results = MutableStateFlow<List<RecoverableFile>>(emptyList())
    val results: StateFlow<List<RecoverableFile>> = _results.asStateFlow()

    private val _filter = MutableStateFlow(Filter())
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _recovered = MutableStateFlow<Set<Long>>(emptySet())
    val recovered: StateFlow<Set<Long>> = _recovered.asStateFlow()

    private val _recovery = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val recovery: StateFlow<RecoveryState> = _recovery.asStateFlow()

    /** Filtering and sorting tens of thousands of rows happens off the main thread. */
    val visible: StateFlow<FilteredResults> = combine(_results, _filter) { all, filter -> filterResults(all, filter) }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FilteredResults())

    private var sizeById: Map<Long, Long> = emptyMap()

    /** Cost is proportional to the selection, not to the whole result list. */
    val selectionSummary: StateFlow<SelectionSummary> = combine(_results, _selection) { _, ids ->
        SelectionSummary(ids.size, ids.sumOf { sizeById[it] ?: 0L })
    }.flowOn(dispatchers.default).stateIn(viewModelScope, SharingStarted.Eagerly, SelectionSummary())

    private var scanJob: Job? = null
    private var recoveryJob: Job? = null

    fun refreshAccess() {
        _hasAccess.value = permissions.hasFileAccess()
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        _selection.value = emptySet()
        _scan.value = ScanState.Running(ScanProgress())
        scanJob = viewModelScope.launch {
            scanStorage().collect { update ->
                when (update) {
                    is ScanUpdate.Progress -> _scan.value = ScanState.Running(update.progress)
                    is ScanUpdate.Finished -> publish(update.files)
                }
            }
        }
    }

    /** Cancelling the collector cancels the whole scan; no late progress event can revive the Running state. */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        val kept = _results.value
        _scan.value = if (kept.isEmpty()) ScanState.Idle else ScanState.Done(kept.size, kept.sumOf { it.size })
    }

    private fun publish(files: List<RecoverableFile>) {
        sizeById = files.associate { it.id to it.size }
        _recovered.value = emptySet()
        _results.value = files
        _scan.value = ScanState.Done(files.size, files.sumOf { it.size })
    }

    fun setType(type: FileType?) = _filter.update { it.copy(type = type) }
    fun setSource(source: SourceFilter) = _filter.update { it.copy(source = source) }
    fun setSort(sort: SortOrder) = _filter.update { it.copy(sort = sort) }

    fun toggle(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { _selection.value = emptySet() }

    /** Selects everything in the current view, or clears it when it is already fully selected. */
    fun toggleSelectAll() {
        val ids = visible.value.items.mapTo(HashSet()) { it.id }
        _selection.update { current -> if (current.containsAll(ids)) current - ids else current + ids }
    }

    fun recoverSelected() {
        val ids = _selection.value
        recover(_results.value.filter { it.id in ids })
    }

    fun recover(items: List<RecoverableFile>) {
        if (items.isEmpty() || recoveryJob?.isActive == true) return
        _recovery.value = RecoveryState.Running(RecoveryProgress(0, items.size, 0, items.sumOf { it.size }))
        recoveryJob = viewModelScope.launch {
            recoverFiles(items).collect { update ->
                when (update) {
                    is RecoveryUpdate.Progress -> _recovery.value = RecoveryState.Running(update.progress)
                    is RecoveryUpdate.Finished -> {
                        _recovered.update { it + update.summary.recoveredIds }
                        _selection.update { it - update.summary.recoveredIds }
                        _recovery.value = RecoveryState.Done(update.summary)
                    }
                }
            }
        }
    }

    fun cancelRecovery() {
        recoveryJob?.cancel()
        recoveryJob = null
        _recovery.value = RecoveryState.Idle
    }

    fun dismissRecovery() { _recovery.value = RecoveryState.Idle }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as App).container
                MainViewModel(
                    scanStorage = container.scanStorage,
                    recoverFiles = container.recoverFiles,
                    filterResults = container.filterResults,
                    permissions = container.permissionRepository,
                    dispatchers = container.dispatchers,
                )
            }
        }
    }
}
