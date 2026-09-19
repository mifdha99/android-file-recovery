package com.eslam.recovery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.eslam.recovery.domain.model.SortOrder
import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.SourceFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: MainViewModel, onBack: () -> Unit, onPreview: (Int) -> Unit) {
    val visible by vm.visible.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val recovered by vm.recovered.collectAsStateWithLifecycle()
    val summary by vm.selectionSummary.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    val selectedBytes = summary.bytes

    BackHandler(enabled = selecting) { vm.clearSelection() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selecting) Text("${selection.size} selected · ${formatSize(selectedBytes)}")
                    else Text("Recoverable files")
                },
                navigationIcon = {
                    if (selecting) IconButton(vm::clearSelection) { Icon(Icons.Default.Close, "Clear selection") }
                    else IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { IconButton(vm::toggleSelectAll) { Icon(Icons.Default.SelectAll, "Select all") } },
            )
        },
        bottomBar = {
            if (selecting) Surface(tonalElevation = 3.dp) {
                Button(vm::recoverSelected, Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    Icon(Icons.Default.Restore, null)
                    Text("  Recover ${selection.size} files (${formatSize(selectedBytes)})")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = remember(visible.counts) { listOf<FileType?>(null) + FileType.entries.filter { (visible.counts[it] ?: 0) > 0 } }
            PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(filter.type).coerceAtLeast(0), edgePadding = 8.dp) {
                tabs.forEach { type ->
                    Tab(
                        selected = filter.type == type,
                        onClick = { vm.setType(type) },
                        text = { Text("${type.label} (${visible.counts[type] ?: 0})") },
                    )
                }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuChip(Icons.AutoMirrored.Filled.Sort, filter.sort.label, SortOrder.entries.map { it.label }) {
                    vm.setSort(SortOrder.entries[it])
                }
                MenuChip(Icons.Default.FilterList, filter.source.label, SourceFilter.entries.map { it.label }) {
                    vm.setSource(SourceFilter.entries[it])
                }
            }

            val items = visible.items
            val grid = filter.type == null || filter.type == FileType.IMAGE || filter.type == FileType.VIDEO
            when {
                items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing here") }
                grid -> LazyVerticalGrid(
                    GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }, contentType = { _, _ -> "cell" }) { index, item ->
                        GridCell(
                            item, selected = item.id in selection, recovered = item.id in recovered,
                            onClick = { if (selecting) vm.toggle(item.id) else onPreview(index) },
                            onToggle = { vm.toggle(item.id) },
                        )
                    }
                }
                else -> LazyColumn {
                    itemsIndexed(items, key = { _, it -> it.id }, contentType = { _, _ -> "row" }) { index, item ->
                        ListRow(
                            item, selected = item.id in selection, recovered = item.id in recovered,
                            onClick = { if (selecting) vm.toggle(item.id) else onPreview(index) },
                            onToggle = { vm.toggle(item.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, text -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

@Composable
fun Thumbnail(item: RecoverableFile, modifier: Modifier = Modifier) {
    if (item.type == FileType.IMAGE || item.type == FileType.VIDEO) {
        val context = LocalContext.current
        // Fixed small decode size keeps scrolling smooth with thousands of multi-megabyte photos.
        val request = remember(item.id) { ImageRequest.Builder(context).data(item).size(Size(256, 256)).build() }
        AsyncImage(request, item.displayName, modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
            Icon(item.type.icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(item: RecoverableFile, selected: Boolean, recovered: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = onClick, onLongClick = onToggle)
    ) {
        Thumbnail(item, Modifier.fillMaxSize())
        if (item.type == FileType.VIDEO) {
            Icon(Icons.Default.PlayCircle, null, Modifier.align(Alignment.Center).size(32.dp), tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (item.type != FileType.IMAGE && item.type != FileType.VIDEO) {
                Text(item.displayName, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${formatSize(item.size)} · ${if (recovered) "Recovered" else item.source.label}",
                color = if (recovered) Color(0xFF7CFF9B) else Color.White,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(2.dp), onToggle)
        if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(item: RecoverableFile, selected: Boolean, recovered: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onToggle).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(item, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${formatSize(item.size)} · ${if (recovered) "Recovered" else item.source.label} · ${formatDate(item.lastModified)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            )
        }
        SelectMark(selected, Modifier, onToggle)
    }
}

@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    Icon(
        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        if (selected) "Selected" else "Select",
        modifier.size(28.dp).clip(CircleShape).clickable(onClick = onToggle).padding(2.dp),
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
    )
}
