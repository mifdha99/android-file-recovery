package com.eslam.recovery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.RecoverableFile
import com.eslam.recovery.domain.model.Source
import com.eslam.recovery.domain.model.SourceFilter

@Composable
fun HomeScreen(vm: MainViewModel, onOpenResults: () -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("File Recovery", style = MaterialTheme.typography.headlineMedium)

            when (val s = scan) {
                ScanState.Idle -> {
                    Text(
                        "Scans all storage in parallel for deleted photos, videos and files: system trash, " +
                            "recycle bins, leftover thumbnails, hidden media and app caches.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(vm::startScan, Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Scan for deleted files")
                    }
                }

                is ScanState.Running -> Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(64.dp))
                        Text(
                            "${formatCount(s.progress.found)} found · ${formatSize(s.progress.foundBytes)}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (s.progress.finishing) "Checking thumbnails…"
                            else "${formatCount(s.progress.files)} files in ${formatCount(s.progress.dirs)} folders scanned",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(vm::cancelScan) { Text("Cancel") }
                    }
                }

                is ScanState.Done -> {
                    val deleted = remember(results) { results.filter { it.source in SourceFilter.DELETED.sources } }
                    val other = remember(results) { results.filter { it.source !in SourceFilter.DELETED.sources } }
                    fun open(type: FileType?, source: SourceFilter) { vm.setType(type); vm.setSource(source); onOpenResults() }

                    // Deleted items and merely hidden media are never added together: one number
                    // for both would promise far more than can really be restored.
                    HeadlineCard(
                        title = "${formatCount(deleted.size.toLong())} deleted files",
                        subtitle = "System trash, recycle bins, thumbnails",
                        items = deleted, emphasized = true,
                    ) { open(null, SourceFilter.DELETED) }
                    HeadlineCard(
                        title = "${formatCount(other.size.toLong())} hidden or cached files",
                        subtitle = "Not shown in the gallery. May include copies of deleted media.",
                        items = other, emphasized = false,
                    ) { open(null, SourceFilter.ALL) }

                    Breakdown(results, onOpenType = { open(it, SourceFilter.ALL) }, onOpenSource = { open(null, SourceFilter.of(it)) })
                    OutlinedButton(vm::startScan, Modifier.fillMaxWidth()) { Text("Scan again") }
                }
            }
            Text(
                "Recovered files are copied to the RecoveredFiles folder. Originals are never changed. " +
                    "Files erased from trash long ago cannot be restored by any app without root.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeadlineCard(title: String, subtitle: String, items: List<RecoverableFile>, emphasized: Boolean, onClick: () -> Unit) {
    val bytes = remember(items) { items.sumOf { it.size } }
    val colors = if (emphasized) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = colors) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("${formatSize(bytes)} · $subtitle", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open")
        }
    }
}

@Composable
private fun Breakdown(results: List<RecoverableFile>, onOpenType: (FileType) -> Unit, onOpenSource: (Source) -> Unit) {
    val byType = remember(results) { results.groupBy { it.type } }
    val bySource = remember(results) { results.groupBy { it.source } }

    Text("By type", style = MaterialTheme.typography.titleMedium)
    FileType.entries.forEach { type ->
        val items = byType[type] ?: return@forEach
        StatRow(type.icon, type.label, null, items) { onOpenType(type) }
    }
    Text("By location", style = MaterialTheme.typography.titleMedium)
    Source.entries.forEach { source ->
        val items = bySource[source] ?: return@forEach
        StatRow(null, source.label, source.description, items) { onOpenSource(source) }
    }
}

@Composable
private fun StatRow(icon: ImageVector?, title: String, subtitle: String?, items: List<RecoverableFile>, onClick: () -> Unit) {
    val bytes = remember(items) { items.sumOf { it.size } }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCount(items.size.toLong()), style = MaterialTheme.typography.titleSmall)
                Text(formatSize(bytes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

val FileType.icon: ImageVector
    get() = when (this) {
        FileType.IMAGE -> Icons.Default.Image
        FileType.VIDEO -> Icons.Default.Videocam
        FileType.AUDIO -> Icons.Default.AudioFile
        FileType.DOCUMENT -> Icons.Default.Description
        FileType.ARCHIVE -> Icons.Default.FolderZip
        FileType.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
