package com.eslam.recovery.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.eslam.recovery.domain.model.FileType
import com.eslam.recovery.domain.model.RecoverableFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(vm: MainViewModel, startIndex: Int, onBack: () -> Unit) {
    val visible by vm.visible.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val recovered by vm.recovered.collectAsStateWithLifecycle()
    // Snapshot: the list must not reshuffle under the pager while the user is looking at it.
    val items = remember { visible.items }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    val item = items[pager.currentPage]
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pager.currentPage + 1} / ${items.size}") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatSize(item.size)} · ${item.source.label} · ${formatDate(item.lastModified)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    expiryText(item.expiresAt)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text(item.path, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.toggle(item.id) }, Modifier.weight(1f)) {
                            Text(if (item.id in selection) "Deselect" else "Select")
                        }
                        if (item.carve == null && item.type != FileType.IMAGE) {
                            FilledTonalButton({ openExternally(context, item) }, Modifier.weight(1f)) { Text("Open") }
                        }
                        Button({ vm.recover(listOf(item)) }, Modifier.weight(1f)) {
                            Text(if (item.id in recovered) "Recover again" else "Recover")
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding).background(Color.Black), userScrollEnabled = !zoomed, beyondViewportPageCount = 1) { page ->
            val current = items[page]
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                when (current.type) {
                    FileType.IMAGE -> ZoomableImage(current, onZoomChanged = { if (page == pager.currentPage) zoomed = it })
                    FileType.VIDEO -> {
                        val request = remember(current.id) { ImageRequest.Builder(context).data(current).size(Size(1280, 1280)).build() }
                        AsyncImage(request, current.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        IconButton({ openExternally(context, current) }, Modifier.size(88.dp)) {
                            Icon(Icons.Default.PlayCircle, "Play", Modifier.size(72.dp), tint = Color.White)
                        }
                    }
                    else -> Icon(current.type.icon, null, Modifier.size(96.dp), tint = Color.White)
                }
            }
        }
    }
    LaunchedEffect(pager.currentPage) { zoomed = false }
}

private fun openExternally(context: Context, item: RecoverableFile) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.path))
        // The detected mime is passed explicitly: trashed / recycle-bin names often have no usable extension.
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, "Cannot open this file", Toast.LENGTH_SHORT).show()
    }
}
